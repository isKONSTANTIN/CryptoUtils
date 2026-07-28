package su.knst.crypto.tests.integration.backup;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        return data(hex, "abcdef", showQr);
    }

    private static ShareCardImage.ShareCardData data(String hex, String checksumHex, boolean showQr) {
        return new ShareCardImage.ShareCardData(
                "my_backup", 1, 5, 3, LocalDate.of(2026, 7, 28), hex, checksumHex, "SEED", ErrorCorrectionLevel.M, showQr);
    }

    // mirrors BackupCreateCommand's checksum derivation (HexUtils.hexHash(shareBytes).substring(0,
    // 6).toLowerCase) so the e2e test can assert the barcode round-trips to the same value production
    // would have embedded for the given hex content.
    private static String checksumFor(byte[] bytes) throws NoSuchAlgorithmException {
        return HexUtils.hexHash(bytes).substring(0, 6).toLowerCase(Locale.ROOT);
    }

    // GenericMultipleBarcodeReader (rather than a single MultiFormatReader.decode call) is needed
    // here because the card also contains a QR code: restricting to CODE_128 alone still isn't
    // enough for a plain decode() to reliably locate the much smaller 1D barcode sharing the frame
    // with a dominant QR code, but the multi-barcode reader's region-splitting search finds it.
    private static String decodeCode128(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));

        Map<DecodeHintType, Object> hints = Map.of(
                DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.CODE_128),
                DecodeHintType.TRY_HARDER, Boolean.TRUE
        );

        try {
            Result[] results = new GenericMultipleBarcodeReader(new MultiFormatReader()).decodeMultiple(bitmap, hints);
            return results.length > 0 ? results[0].getText() : null;
        } catch (com.google.zxing.NotFoundException e) {
            return null;
        }
    }

    // Barcode detection isn't perfectly reliable for every possible bit pattern at this module
    // size (same caveat BackupCreateCommand's split-retry loop works around), so a single random
    // payload occasionally fails to decode even though encoding produced a valid QR. Retrying with
    // fresh random bytes mirrors that production behavior instead of flaking on the rare miss.
    @Test
    void embeddedQrDecodesBackToTheExactHexPayload() throws WriterException, IOException {
        String hex = null;
        String decoded = null;
        int attempt = 0;

        // do-while, not a pre-checked for-loop: with both values starting out null,
        // Objects.equals(null, null) is true, so a pre-checked "while not equal" condition would
        // never even run the body once and the assertion below would trivially pass on two nulls.
        do {
            byte[] shareBytes = new byte[16];
            RANDOM.nextBytes(shareBytes);
            hex = HexUtils.bytesToHex(shareBytes);

            BufferedImage image = ShareCardImage.build(data(hex));
            ImageIO.write(image, "png", IMAGE_PATH.toFile());

            decoded = new SimpleQRCodeWorker().readCode(IMAGE_PATH.toString());
            attempt++;
        } while (attempt < 10 && !Objects.equals(hex, decoded));

        assertEquals(hex, decoded);
    }

    // End-to-end: the QR payload, the printed checksum, and the Code128 barcode must all agree with
    // each other for a given share, exactly as they would for a scanned printed card in the field -
    // decode the QR back to hex, decode the barcode back to a checksum, and independently recompute
    // the checksum from the decoded hex, then assert all three values coincide.
    @Test
    void checksumHexPayloadAndBarcodeAllAgree() throws WriterException, IOException, NoSuchAlgorithmException {
        String hex = null;
        String checksum = null;
        String decodedHex = null;
        String decodedChecksum = null;
        int attempt = 0;

        // do-while: see embeddedQrDecodesBackToTheExactHexPayload for why a pre-checked "while not
        // equal" loop would never run when both sides start out null.
        do {
            byte[] shareBytes = new byte[16];
            RANDOM.nextBytes(shareBytes);
            hex = HexUtils.bytesToHex(shareBytes);
            checksum = checksumFor(shareBytes);

            BufferedImage image = ShareCardImage.build(data(hex, checksum, true));
            ImageIO.write(image, "png", IMAGE_PATH.toFile());

            decodedHex = new SimpleQRCodeWorker().readCode(IMAGE_PATH.toString());
            decodedChecksum = decodeCode128(IMAGE_PATH);
            attempt++;
        } while (attempt < 10 && (!Objects.equals(hex, decodedHex) || !Objects.equals(checksum, decodedChecksum)));

        assertEquals(hex, decodedHex, "QR payload must decode back to the exact hex content");
        assertEquals(checksum, decodedChecksum, "barcode must decode back to the exact checksum");
        assertEquals(checksum, checksumFor(HexUtils.hexStringToByteArray(decodedHex)),
                "checksum recomputed from the decoded hex content must match the printed/barcoded checksum");
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
