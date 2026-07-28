package su.knst.crypto.utils.codes;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LabeledQrImage {
    private static final int MARGIN = 16;
    private static final int GAP = 12;
    private static final int HEADER_SIZE = 22;
    private static final int PAYLOAD_SIZE = 16;
    private static final TrueTypeFont FONT = loadFont();

    private static TrueTypeFont loadFont() {
        try (InputStream in = LabeledQrImage.class.getResourceAsStream("/su/knst/crypto/fonts/RobotoMono.ttf")) {
            return TrueTypeFont.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BufferedImage build(String headerLine, String hexPayload, int qrPixelSize, ErrorCorrectionLevel level) throws WriterException {
        BitMatrix matrix = new MultiFormatWriter().encode(
                hexPayload,
                BarcodeFormat.QR_CODE,
                qrPixelSize,
                qrPixelSize,
                Map.of(EncodeHintType.ERROR_CORRECTION, level)
        );

        BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(matrix);

        int contentWidth = Math.max(qrPixelSize, 500);

        List<String> payloadLines = wrap(hexPayload, contentWidth - 2 * MARGIN);

        int headerHeight = Math.round(lineHeight(HEADER_SIZE));
        int payloadLineHeight = Math.round(lineHeight(PAYLOAD_SIZE));
        int payloadHeight = payloadLines.size() * payloadLineHeight;

        int canvasWidth = contentWidth + 2 * MARGIN;
        int canvasHeight = MARGIN + headerHeight + GAP + qrPixelSize + GAP + payloadHeight + MARGIN;

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, canvasWidth, canvasHeight);
        g.setColor(Color.BLACK);

        int y = MARGIN;

        float headerBaseline = y + ascentPixels(HEADER_SIZE);
        drawText(g, headerLine, centeredX(Math.round(stringWidth(headerLine, HEADER_SIZE)), canvasWidth), headerBaseline, HEADER_SIZE);
        y += headerHeight + GAP;

        int qrX = centeredX(qrPixelSize, canvasWidth);
        g.drawImage(qrImage, qrX, y, null);
        y += qrPixelSize + GAP;

        for (String line : payloadLines) {
            float baseline = y + ascentPixels(PAYLOAD_SIZE);
            drawText(g, line, centeredX(Math.round(stringWidth(line, PAYLOAD_SIZE)), canvasWidth), baseline, PAYLOAD_SIZE);
            y += payloadLineHeight;
        }

        g.dispose();

        return canvas;
    }

    private static int centeredX(int contentWidth, int canvasWidth) {
        return Math.max(0, (canvasWidth - contentWidth) / 2);
    }

    private static List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();

        int charWidth = Math.max(1, Math.round(advance(PAYLOAD_SIZE)));
        int charsPerLine = Math.max(1, maxWidth / charWidth);

        for (int i = 0; i < text.length(); i += charsPerLine)
            lines.add(text.substring(i, Math.min(text.length(), i + charsPerLine)));

        return lines;
    }

    private static float scale(int pixelSize) {
        return pixelSize / (float) FONT.unitsPerEm();
    }

    private static float advance(int pixelSize) {
        return FONT.advanceWidth() * scale(pixelSize);
    }

    private static float ascentPixels(int pixelSize) {
        return FONT.ascender() * scale(pixelSize);
    }

    private static float lineHeight(int pixelSize) {
        return (FONT.ascender() - FONT.descender()) * scale(pixelSize);
    }

    private static float stringWidth(String text, int pixelSize) {
        return text.length() * advance(pixelSize);
    }

    private static void drawText(Graphics2D g, String text, float x, float baselineY, int pixelSize) {
        float scale = scale(pixelSize);
        float cursor = x;

        for (int i = 0; i < text.length(); i++) {
            GeneralPath glyph = FONT.glyphPath(text.charAt(i));

            AffineTransform transform = new AffineTransform();
            transform.translate(cursor, baselineY);
            transform.scale(scale, -scale);

            g.fill(transform.createTransformedShape(glyph));

            cursor += advance(pixelSize);
        }
    }
}
