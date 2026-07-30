package su.knst.crypto.core.render;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.ReaderException;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Encoding and decoding of QR codes. Nothing here decides whether a code is good enough for a given
 * purpose - it either produces an image or reads one back, and the caller applies the policy.
 */
public final class QrCodec {
    // Fallback preference order for card rendering: most error correction first, stepping down a
    // notch when a payload is too large to fit. This is NOT the ISO 18004 strength ranking used by
    // describeLevel().
    public static final ErrorCorrectionLevel[] LEVELS = {
            ErrorCorrectionLevel.H, ErrorCorrectionLevel.Q, ErrorCorrectionLevel.M, ErrorCorrectionLevel.L
    };

    private static final ErrorCorrectionLevel[] STRENGTH_ORDER = {
            ErrorCorrectionLevel.L, ErrorCorrectionLevel.M, ErrorCorrectionLevel.Q, ErrorCorrectionLevel.H
    };

    private QrCodec() {
    }

    public static BitMatrix encode(String payload, ErrorCorrectionLevel level, int sizePx, int margin)
            throws WriterException {
        return new MultiFormatWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                Map.of(EncodeHintType.ERROR_CORRECTION, level, EncodeHintType.MARGIN, margin)
        );
    }

    public static String decode(Path path) throws IOException {
        BufferedImage image;

        try (FileInputStream input = new FileInputStream(path.toFile())) {
            image = ImageIO.read(input);
        }

        return decode(image);
    }

    /**
     * Reads a QR code straight out of an in-memory image. The backup flow verifies every rendered
     * share by decoding it back, and routing that through a temporary file would spill share QR
     * codes into the system temp directory at its default permissions, while every intended output
     * is written owner-only.
     *
     * @return the decoded payload, or null if no code could be read
     */
    public static String decode(BufferedImage image) {
        LuminanceSource source = new BufferedImageLuminanceSource(image);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        // deliberately NOT setting PURE_BARCODE: ZXing's QRCodeReader checks hints.containsKey(...)
        // for this one, not the boolean value, so putting it at all - even as FALSE - tells the
        // detector to assume the whole image IS the barcode edge-to-edge. That breaks every photo
        // where the code only occupies part of a larger frame, which is the normal case here.

        // a real photo's lighting is uneven enough that the two binarizers regularly succeed
        // on different images: HybridBinarizer adapts per local block (better under gradients/
        // shadows), GlobalHistogramBinarizer uses a single image-wide threshold (better when
        // the local-block estimate itself gets thrown off by noise or a busy background)
        List<Binarizer> binarizers = List.of(
                new HybridBinarizer(source),
                new GlobalHistogramBinarizer(source)
        );

        String result = tryDecode(binarizers, hints);

        if (result != null)
            return result;

        // last-resort fallback: assume the code fills the image edge-to-edge after all.
        // Measured against freshly generated (uncropped, noise-free) QR renders, the
        // generic partial-frame detection above still misses a real ~10-15% of otherwise
        // perfectly valid codes; PURE_BARCODE catches those. Only tried once the two
        // attempts above already failed, so it can only recover extra codes, never break
        // the partial-frame photo case those are tuned for.
        Map<DecodeHintType, Object> pureBarcodeHints = new EnumMap<>(hints);
        pureBarcodeHints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);

        return tryDecode(binarizers, pureBarcodeHints);
    }

    private static String tryDecode(List<Binarizer> binarizers, Map<DecodeHintType, Object> hints) {
        MultiFormatReader reader = new MultiFormatReader();
        reader.setHints(hints);

        for (Binarizer binarizer : binarizers) {
            try {
                return reader.decode(new BinaryBitmap(binarizer), hints).getText();
            } catch (ReaderException ignored) {
                // try the next binarizer
            } finally {
                reader.reset();
            }
        }

        return null;
    }

    public static String describeLevel(ErrorCorrectionLevel level) {
        String name = switch (level) {
            case L -> "Low";
            case M -> "Medium";
            case Q -> "Quartile";
            case H -> "High";
        };

        int position = Arrays.asList(STRENGTH_ORDER).indexOf(level) + 1;

        return name + " (" + position + "/" + STRENGTH_ORDER.length + ")";
    }
}
