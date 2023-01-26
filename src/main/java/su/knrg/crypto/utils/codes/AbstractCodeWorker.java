package su.knrg.crypto.utils.codes;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public abstract class AbstractCodeWorker {
    public abstract void generateCode(String data, String path, int width, int height, ErrorCorrectionLevel correctionLevel) throws WriterException, IOException;

    public abstract String readCode(String path) throws IOException, NotFoundException;
}
