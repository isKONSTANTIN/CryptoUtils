package su.knst.crypto.tests.integration.backup;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import su.knst.crypto.core.render.PngWriter;
import su.knst.crypto.core.render.PrintGeometry;
import su.knst.crypto.core.render.QrCodec;
import su.knst.crypto.core.render.TagImage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TagImageTest {

    static final Path IMAGE_PATH = Path.of("tag_image_test.png");

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(IMAGE_PATH);
    }

    private static TagImage.TagData data(String tagName, String checksumHex) {
        return new TagImage.TagData(tagName, 1, 5, checksumHex);
    }

    // The tag's checksum barcode is rendered rotated 90deg (bars horizontal, block tall - see
    // TagImage's class doc) so it decodes as a normal CODE_128 barcode once rotated back.
    private static BufferedImage rotateBack90(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();

        BufferedImage rotated = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rotated.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, h, w);

        AffineTransform transform = new AffineTransform();
        transform.translate(h / 2.0, w / 2.0);
        transform.rotate(-Math.PI / 2);
        transform.translate(-w / 2.0, -h / 2.0);

        g.drawImage(src, transform, null);
        g.dispose();

        return rotated;
    }

    private static String decodeCode128(BufferedImage image) {
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));

        Map<DecodeHintType, Object> hints = Map.of(
                DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.CODE_128),
                DecodeHintType.TRY_HARDER, Boolean.TRUE
        );

        try {
            Result[] results = new GenericMultipleBarcodeReader(new MultiFormatReader()).decodeMultiple(bitmap, hints);
            return results.length > 0 ? results[0].getText() : null;
        } catch (NotFoundException e) {
            return null;
        }
    }

    @Test
    void verticalBarcodeDecodesBackToTheChecksum() throws WriterException {
        String checksum = "ab12cd";

        BufferedImage image = TagImage.build(data("My Container", checksum));
        BufferedImage rotatedBack = rotateBack90(image);

        assertEquals(checksum, decodeCode128(rotatedBack));
    }

    @Test
    void tagHasNoDecodableQrCode() throws WriterException, IOException {
        BufferedImage image = TagImage.build(data("My Container", "ab12cd"));
        ImageIO.write(image, "png", IMAGE_PATH.toFile());

        assertNull(QrCodec.decode(IMAGE_PATH));
    }

    @Test
    void tagHeightIsFixedRegardlessOfTagNameLength() throws WriterException {
        BufferedImage shortName = TagImage.build(data("A", "ab12cd"));
        BufferedImage longName = TagImage.build(data("A Much Longer Container Name For This Backup", "ab12cd"));

        assertEquals(shortName.getHeight(), longName.getHeight());
    }

    @Test
    void tagWidthGrowsWithLongerTagName() throws WriterException {
        BufferedImage shortName = TagImage.build(data("A", "ab12cd"));
        BufferedImage longName = TagImage.build(data("A Much Longer Container Name For This Backup", "ab12cd"));

        assertTrue(longName.getWidth() > shortName.getWidth());
    }

    @Test
    void pngWrittenByWritePngContainsPhysChunk(@TempDir Path tempDir) throws IOException, WriterException {
        BufferedImage image = TagImage.build(data("My Container", "ab12cd"));
        Path path = tempDir.resolve("tag.png");

        PngWriter.write(image, path);

        byte[] bytes = Files.readAllBytes(path);
        String content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        int chunkIndex = content.indexOf("pHYs");

        assertTrue(chunkIndex >= 0, "pHYs chunk not found in written PNG");

        int dataStart = chunkIndex + 4;
        int pixelsPerUnitXAxis =
                ((bytes[dataStart] & 0xFF) << 24)
                | ((bytes[dataStart + 1] & 0xFF) << 16)
                | ((bytes[dataStart + 2] & 0xFF) << 8)
                | (bytes[dataStart + 3] & 0xFF);

        // written at 300 DPI nominal, but the buffer is a 6x supersample, so the recorded density
        // must double too or the physical print size would come out twice as large
        assertEquals(PrintGeometry.PIXELS_PER_METER, pixelsPerUnitXAxis);
    }
}
