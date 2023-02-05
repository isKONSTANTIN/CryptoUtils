package su.knst.crypto.utils.codes;

import com.google.zxing.NotFoundException;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.IOException;

public abstract class AbstractCodeWorker {
    public abstract void generateCode(String data, String path, int width, int height, ErrorCorrectionLevel correctionLevel) throws WriterException, IOException;

    public abstract String readCode(String path) throws IOException, NotFoundException;
}
