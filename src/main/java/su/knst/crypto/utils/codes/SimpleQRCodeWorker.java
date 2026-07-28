package su.knst.crypto.utils.codes;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
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
        LuminanceSource source = new BufferedImageLuminanceSource(image);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        // deliberately NOT setting PURE_BARCODE: ZXing's QRCodeReader checks hints.containsKey(...)
        // for this one, not the boolean value, so putting it at all - even as FALSE - tells the
        // detector to assume the whole image IS the barcode edge-to-edge. That breaks every photo
        // where the code only occupies part of a larger frame, which is the normal case here.

        MultiFormatReader reader = new MultiFormatReader();
        reader.setHints(hints);

        // a real photo's lighting is uneven enough that the two binarizers regularly succeed
        // on different images: HybridBinarizer adapts per local block (better under gradients/
        // shadows), GlobalHistogramBinarizer uses a single image-wide threshold (better when
        // the local-block estimate itself gets thrown off by noise or a busy background)
        List<Binarizer> binarizers = List.of(
                new HybridBinarizer(source),
                new GlobalHistogramBinarizer(source)
        );

        for (Binarizer binarizer : binarizers) {
            try {
                return reader.decode(new BinaryBitmap(binarizer), hints).getText();
            } catch (ReaderException ignored) {
                // try the next binarizer
            } finally {
                reader.reset();
            }
        }

        // last-resort fallback: assume the code fills the image edge-to-edge after all.
        // Measured against freshly generated (uncropped, noise-free) QR renders, the
        // generic partial-frame detection above still misses a real ~10-15% of otherwise
        // perfectly valid codes; PURE_BARCODE catches those. Only tried once the two
        // attempts above already failed, so it can only recover extra codes, never break
        // the partial-frame photo case those are tuned for.
        Map<DecodeHintType, Object> pureBarcodeHints = new EnumMap<>(hints);
        pureBarcodeHints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        reader.setHints(pureBarcodeHints);

        for (Binarizer binarizer : binarizers) {
            try {
                return reader.decode(new BinaryBitmap(binarizer), pureBarcodeHints).getText();
            } catch (ReaderException ignored) {
                // try the next binarizer
            } finally {
                reader.reset();
            }
        }

        return null;
    }
}
