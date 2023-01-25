package su.knrg.crypto.utils;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SimpleQRCodeWorker {
    public static void generateQRCode(String data, String path, int width, int height, ErrorCorrectionLevel correctionLevel) throws WriterException, IOException {
        BitMatrix matrix = new MultiFormatWriter().encode(
                data,
                BarcodeFormat.QR_CODE,
                width,
                height,
                Map.of(EncodeHintType.ERROR_CORRECTION, correctionLevel)
        );

        MatrixToImageWriter.writeToPath(matrix, path.substring(path.lastIndexOf('.') + 1), Path.of(path));
    }

    public static String readQRCode(String path) throws IOException, NotFoundException {
        BufferedImage image = ImageIO.read(new FileInputStream(path));
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));

        return new MultiFormatReader().decode(binaryBitmap).getText();
    }
}
