package su.knrg.crypto.utils.codes;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.pdf417.PDF417Writer;
import com.google.zxing.pdf417.encoder.Dimensions;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class SimplePDF417Worker extends AbstractCodeWorker {

    @Override
    public void generateCode(String data, String path, int width, int height, ErrorCorrectionLevel correctionLevel) throws WriterException, IOException {
        BitMatrix matrix = new PDF417Writer().encode(
                data,
                BarcodeFormat.PDF_417,
                width,
                height,
                Map.of(EncodeHintType.ERROR_CORRECTION, 0,
                        EncodeHintType.PDF417_COMPACTION, "TEXT",
                        EncodeHintType.PDF417_COMPACT, "false",
                        EncodeHintType.PDF417_DIMENSIONS, new Dimensions(1,100,1,100)
                )
        );

        MatrixToImageWriter.writeToPath(matrix, path.substring(path.lastIndexOf('.') + 1), Path.of(path));
    }

    @Override
    public String readCode(String path) throws IOException, NotFoundException {
        BufferedImage image = ImageIO.read(new FileInputStream(path));
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));

        return new MultiFormatReader().decode(binaryBitmap).getText();
    }
}
