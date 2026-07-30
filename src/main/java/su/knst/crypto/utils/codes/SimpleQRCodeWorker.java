package su.knst.crypto.utils.codes;

import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import su.knst.crypto.core.render.QrCodec;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

// Thin AbstractCodeWorker adapter over core.render.QrCodec, kept only for the generic `qr` command;
// everything else calls QrCodec directly.
public class SimpleQRCodeWorker extends AbstractCodeWorker {
    @Override
    public void generateCode(String data, String path, int width, int height, ErrorCorrectionLevel correctionLevel) throws WriterException, IOException {
        BitMatrix matrix = QrCodec.encode(data, correctionLevel, width, 4);

        MatrixToImageWriter.writeToPath(matrix, path.substring(path.lastIndexOf('.') + 1), Path.of(path));
    }

    @Override
    public String readCode(String path) throws IOException {
        return QrCodec.decode(Path.of(path));
    }

    public String readCode(BufferedImage image) {
        return QrCodec.decode(image);
    }
}
