package su.knst.crypto.tests.integration.backup;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.codes.LabeledQrImage;
import su.knst.crypto.utils.codes.SimpleQRCodeWorker;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class LabeledQrImageTest {

    static final Path IMAGE_PATH = Path.of("labeled_qr_image_test.png");
    static final SecureRandom RANDOM = new SecureRandom();

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(IMAGE_PATH);
    }

    @Test
    void embeddedQrDecodesBackToTheExactHexPayload() throws WriterException, IOException {
        byte[] shareBytes = new byte[16];
        RANDOM.nextBytes(shareBytes);
        String hex = HexUtils.bytesToHex(shareBytes);

        BufferedImage image = LabeledQrImage.build("my_backup 1/5 SEED", hex, 300, ErrorCorrectionLevel.M);
        ImageIO.write(image, "png", IMAGE_PATH.toFile());

        String decoded = new SimpleQRCodeWorker().readCode(IMAGE_PATH.toString());

        assertEquals(hex, decoded);
    }

    @Test
    void payloadTooLargeForQrCapacityThrows() {
        StringBuilder hugeHex = new StringBuilder();

        for (int i = 0; i < 10_000; i++)
            hugeHex.append("AB");

        assertThrows(WriterException.class, () ->
                LabeledQrImage.build("header", hugeHex.toString(), 300, ErrorCorrectionLevel.M));
    }
}
