package su.knst.crypto.utils.codes;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LabeledQrImage {
    private static final int MARGIN = 16;
    private static final int GAP = 12;
    private static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    private static final Font PAYLOAD_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 16);

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

        // a throwaway image/graphics just to measure text before the real canvas size is known
        BufferedImage measurer = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D measuringGraphics = measurer.createGraphics();

        measuringGraphics.setFont(PAYLOAD_FONT);
        List<String> payloadLines = wrap(hexPayload, measuringGraphics.getFontMetrics(), contentWidth - 2 * MARGIN);

        measuringGraphics.setFont(HEADER_FONT);
        FontMetrics headerMetrics = measuringGraphics.getFontMetrics();
        FontMetrics payloadMetrics = measuringGraphics.getFontMetrics(PAYLOAD_FONT);
        measuringGraphics.dispose();

        int headerHeight = headerMetrics.getHeight();
        int payloadHeight = payloadLines.size() * payloadMetrics.getHeight();

        int canvasWidth = contentWidth + 2 * MARGIN;
        int canvasHeight = MARGIN + headerHeight + GAP + qrPixelSize + GAP + payloadHeight + MARGIN;

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, canvasWidth, canvasHeight);
        g.setColor(Color.BLACK);

        int y = MARGIN;

        g.setFont(HEADER_FONT);
        FontMetrics hm = g.getFontMetrics();
        g.drawString(headerLine, centeredX(hm.stringWidth(headerLine), canvasWidth), y + hm.getAscent());
        y += headerHeight + GAP;

        int qrX = centeredX(qrPixelSize, canvasWidth);
        g.drawImage(qrImage, qrX, y, null);
        y += qrPixelSize + GAP;

        g.setFont(PAYLOAD_FONT);
        FontMetrics pm = g.getFontMetrics();
        for (String line : payloadLines) {
            g.drawString(line, centeredX(pm.stringWidth(line), canvasWidth), y + pm.getAscent());
            y += pm.getHeight();
        }

        g.dispose();

        return canvas;
    }

    private static int centeredX(int contentWidth, int canvasWidth) {
        return Math.max(0, (canvasWidth - contentWidth) / 2);
    }

    private static List<String> wrap(String text, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();

        int charWidth = Math.max(1, metrics.charWidth('0'));
        int charsPerLine = Math.max(1, maxWidth / charWidth);

        for (int i = 0; i < text.length(); i += charsPerLine)
            lines.add(text.substring(i, Math.min(text.length(), i + charsPerLine)));

        return lines;
    }
}
