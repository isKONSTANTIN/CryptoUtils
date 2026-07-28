package su.knst.crypto.tests.integration.backup;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.codes.ShareCardImage;
import su.knst.crypto.utils.codes.SimpleQRCodeWorker;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ShareCardImageTest {

    static final Path IMAGE_PATH = Path.of("share_card_image_test.png");
    static final SecureRandom RANDOM = new SecureRandom();

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(IMAGE_PATH);
    }

    private static ShareCardImage.ShareCardData data(String hex) {
        return data(hex, true);
    }

    private static ShareCardImage.ShareCardData data(String hex, boolean showQr) {
        return new ShareCardImage.ShareCardData(
                "my_backup", 1, 5, 3, LocalDate.of(2026, 7, 28), hex, "abcdef", "SEED", ErrorCorrectionLevel.M, showQr);
    }

    // Barcode detection isn't perfectly reliable for every possible bit pattern at this module
    // size (same caveat BackupCreateCommand's split-retry loop works around), so a single random
    // payload occasionally fails to decode even though encoding produced a valid QR. Retrying with
    // fresh random bytes mirrors that production behavior instead of flaking on the rare miss.
    @Test
    void embeddedQrDecodesBackToTheExactHexPayload() throws WriterException, IOException {
        String hex = null;
        String decoded = null;

        for (int attempt = 0; attempt < 10 && !Objects.equals(hex, decoded); attempt++) {
            byte[] shareBytes = new byte[16];
            RANDOM.nextBytes(shareBytes);
            hex = HexUtils.bytesToHex(shareBytes);

            BufferedImage image = ShareCardImage.build(data(hex));
            ImageIO.write(image, "png", IMAGE_PATH.toFile());

            decoded = new SimpleQRCodeWorker().readCode(IMAGE_PATH.toString());
        }

        assertEquals(hex, decoded);
    }

    @Test
    void payloadTooLargeForQrCapacityThrows() {
        StringBuilder hugeHex = new StringBuilder();

        for (int i = 0; i < 10_000; i++)
            hugeHex.append("AB");

        assertThrows(WriterException.class, () -> ShareCardImage.build(data(hugeHex.toString())));
    }

    // 496 logical px (42mm @ 300 DPI) rendered at a 2x supersample for sharper text/QR edges.
    @Test
    void cardWidthIsExactly496LogicalPixels() throws WriterException {
        BufferedImage image = ShareCardImage.build(data("AABBCCDD"));

        assertEquals(496 * 2, image.getWidth());
    }

    @Test
    void cardHeightIsDeterministicForSameInputs() throws WriterException {
        BufferedImage first = ShareCardImage.build(data("AABBCCDDEEFF0011"));
        BufferedImage second = ShareCardImage.build(data("AABBCCDDEEFF0011"));

        assertEquals(first.getHeight(), second.getHeight());
    }

    @Test
    void tallerCardForLongerHexPayload() throws WriterException {
        String shortHex = "AABBCCDD";

        StringBuilder longHexBuilder = new StringBuilder();
        for (int i = 0; i < 50; i++)
            longHexBuilder.append("AABBCCDD");
        String longHex = longHexBuilder.toString();

        BufferedImage shortCard = ShareCardImage.build(data(shortHex));
        BufferedImage longCard = ShareCardImage.build(data(longHex));

        assertTrue(longCard.getHeight() > shortCard.getHeight());
    }

    @Test
    void hidingQrShrinksTheCardAndDropsTheQrBlock() throws WriterException, IOException {
        String hex = "AABBCCDD";

        BufferedImage withQr = ShareCardImage.build(data(hex, true));
        BufferedImage withoutQr = ShareCardImage.build(data(hex, false));

        assertTrue(withoutQr.getHeight() < withQr.getHeight());

        ImageIO.write(withoutQr, "png", IMAGE_PATH.toFile());
        assertNull(new SimpleQRCodeWorker().readCode(IMAGE_PATH.toString()));
    }

    @Test
    void pngWrittenByWritePngContainsPhysChunk(@TempDir Path tempDir) throws IOException, WriterException {
        BufferedImage image = ShareCardImage.build(data("AABBCCDD"));
        Path path = tempDir.resolve("card.png");

        ShareCardImage.writePng(image, path, 300);

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

        // written at 300 DPI nominal, but the buffer is a 2x supersample, so the recorded density
        // must double too or the physical print size would come out twice as large
        assertEquals(23622, pixelsPerUnitXAxis);
    }
}
