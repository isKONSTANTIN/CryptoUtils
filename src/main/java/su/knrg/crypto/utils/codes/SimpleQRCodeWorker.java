package su.knrg.crypto.utils.codes;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class SimpleQRCodeWorker extends AbstractCodeWorker{
    @Override
    public void generateCode(String data, String path, int width, int height, ErrorCorrectionLevel correctionLevel) throws WriterException, IOException {
        BitMatrix matrix = new MultiFormatWriter().encode(
                data,
                BarcodeFormat.QR_CODE,
                width,
                height,
                Map.of(EncodeHintType.ERROR_CORRECTION, correctionLevel)
        );

        MatrixToImageWriter.writeToPath(matrix, path.substring(path.lastIndexOf('.') + 1), Path.of(path));
    }

    @Override
    public String readCode(String path) throws IOException {
        BufferedImage image = ImageIO.read(new FileInputStream(path));
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));

        Map<DecodeHintType, Object> tmpHintsMap = new EnumMap<DecodeHintType, Object>(
                DecodeHintType.class);
        tmpHintsMap.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        tmpHintsMap.put(DecodeHintType.PURE_BARCODE, Boolean.FALSE);

        for (int i = 0; i < 3; i++) {
            try {
                return new QRCodeReader().decode(binaryBitmap, tmpHintsMap).getText();
            }catch (Exception e) {
                binaryBitmap = binaryBitmap.rotateCounterClockwise45();
            }
        }

        return null;
    }
}
