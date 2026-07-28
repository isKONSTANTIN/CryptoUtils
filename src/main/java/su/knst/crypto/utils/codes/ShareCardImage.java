package su.knst.crypto.utils.codes;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.awt.image.BufferedImage.TYPE_BYTE_BINARY;

/**
 * Renders one Shamir secret-sharing share as a printable "backup card" PNG: a header, a
 * share/threshold/date metadata row, a QR code, a formatted hex dump of the share bytes, a
 * checksum row, a blank notes line and a footer, all inside a dashed cut/fold border.
 *
 * The card is sized to 496px wide, which is exactly 42mm (1/5 of an A4 sheet's 210mm width) at
 * 300 DPI, so a PDF layout stage can later tile cards on a page without any rescaling. Height is
 * not fixed: it grows with the number of wrapped hex lines, which depends on share size.
 */
public final class ShareCardImage {
    // The whole card is laid out in "logical" pixels (design px @ 300 DPI, see class doc), then
    // rendered into a canvas RENDER_SCALE times larger and scaled up via the Graphics2D transform.
    // Vector content (text, lines, fills) simply gets rasterized with more samples this way, which
    // is what makes the small label/hex text noticeably crisper; only the QR bitmap needs special
    // handling (see buildQrImage/drawQr) since blitting an already-rasterized image through a
    // scaled transform would blur its hard edges instead of sharpening them.
    private static final int RENDER_SCALE = 2;

    private static final int CARD_WIDTH = 496;
    private static final int PADDING_TOP = 16;
    private static final int PADDING_SIDE = 20;
    private static final int PADDING_BOTTOM = 18;

    private static final Color TEXT_PRIMARY = new Color(0x11, 0x11, 0x11);
    private static final Color TEXT_SECONDARY_888 = new Color(0x88, 0x88, 0x88);
    private static final Color TEXT_SECONDARY_999 = new Color(0x99, 0x99, 0x99);
    private static final Color LINE_SOLID = new Color(0xDD, 0xDD, 0xDD);
    private static final Color LINE_DASHED_BORDER = new Color(0x99, 0x99, 0x99);
    private static final Color LINE_DASHED_NOTES = new Color(0xBB, 0xBB, 0xBB);

    private static final String REPO_LINK = "github.com/isKONSTANTIN/CryptoUtils";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final int HEX_GROUP_SIZE = 6;
    private static final int HEX_GROUPS_PER_LINE = 10;
    private static final float HEX_FONT_SIZE_DEFAULT = 10f;
    private static final float HEX_FONT_SIZE_MIN = 7f;
    private static final float HEX_LETTER_SPACING_DEFAULT = 0.3f;
    private static final float HEX_LINE_HEIGHT_FACTOR = 1.75f;
    private static final int HEX_BLOCK_PADDING_V = 10;
    private static final int HEX_BLOCK_PADDING_H = 12;

    private static final BasicStroke DASHED_STROKE =
            new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f);

    private static final TrueTypeFont SANS = loadFont("Roboto.ttf");
    private static final TrueTypeFont MONO = loadFont("RobotoMono.ttf");

    public record ShareCardData(
            String backupName,
            int shareIndex,
            int totalShares,
            int threshold,
            LocalDate createdOn,
            String hexPayload,
            String checksumHex,
            String typeLabel,
            ErrorCorrectionLevel errorCorrectionLevel,
            boolean showQr
    ) {
    }

    private ShareCardImage() {
    }

    private static TrueTypeFont loadFont(String fileName) {
        try (InputStream in = ShareCardImage.class.getResourceAsStream("/su/knst/crypto/fonts/" + fileName)) {
            return TrueTypeFont.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BufferedImage build(ShareCardData data) throws WriterException {
        BufferedImage qrImage = data.showQr() ? buildQrImage(data.hexPayload(), data.errorCorrectionLevel()) : null;

        int contentWidth = CARD_WIDTH - 2 * PADDING_SIDE;
        int hexMaxWidth = contentWidth - 2 * HEX_BLOCK_PADDING_H;
        HexLayout hexLayout = layoutHex(data.hexPayload(), hexMaxWidth);

        int canvasHeight = computeCanvasHeight(hexLayout, qrImage);

        BufferedImage canvas = new BufferedImage(
                CARD_WIDTH * RENDER_SCALE, canvasHeight * RENDER_SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(RENDER_SCALE, RENDER_SCALE);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, CARD_WIDTH, canvasHeight);

        float y = PADDING_TOP;

        y = drawHeader(g, data, y);
        y += 12;

        y = drawMetaRow(g, data, y, contentWidth);
        y += 12;

        if (data.showQr()) {
            y = drawQr(g, qrImage, y);
            y += 12;
        }

        y = drawHexBlock(g, hexLayout, y, contentWidth);
        y += 12;

        y = drawChecksumRow(g, data, y, contentWidth);
        y += 12;

        y = drawNotesBlock(g, y, contentWidth);
        y += 10;

        drawFooterRow(g, y, contentWidth);

        g.setColor(LINE_DASHED_BORDER);
        g.setStroke(DASHED_STROKE);
        g.drawRect(0, 0, CARD_WIDTH - 1, canvasHeight - 1);

        g.dispose();

        return canvas;
    }

    public static void writePng(BufferedImage image, Path path, int dpi) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();

        ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(image.getType());
        IIOMetadata metadata = writer.getDefaultImageMetadata(typeSpecifier, writeParam);

        String formatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = new IIOMetadataNode(formatName);

        IIOMetadataNode pHYs = new IIOMetadataNode("pHYs");
        // the caller passes the nominal design DPI (e.g. 300 for a 42mm card); the actual pixel
        // buffer is RENDER_SCALE times denser than that design grid, so the DPI recorded in the
        // file must scale with it too or the physical print size would double.
        int pixelsPerMeter = (int) Math.round((dpi * (double) RENDER_SCALE) / 0.0254);
        pHYs.setAttribute("pixelsPerUnitXAxis", Integer.toString(pixelsPerMeter));
        pHYs.setAttribute("pixelsPerUnitYAxis", Integer.toString(pixelsPerMeter));
        pHYs.setAttribute("unitSpecifier", "meter");
        root.appendChild(pHYs);

        metadata.mergeTree(formatName, root);

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, metadata), writeParam);
        } finally {
            writer.dispose();
        }
    }

    // ---- QR ----------------------------------------------------------------------------------

    private static BufferedImage buildQrImage(String hexPayload, ErrorCorrectionLevel level) throws WriterException {
        BitMatrix matrix = new MultiFormatWriter().encode(
                hexPayload,
                BarcodeFormat.QR_CODE,
                256 * RENDER_SCALE,
                256 * RENDER_SCALE,
                Map.of(EncodeHintType.ERROR_CORRECTION, level, EncodeHintType.MARGIN, 0)
        );

        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private static float drawQr(Graphics2D g, BufferedImage qrImage, float y) {
        int qrLogicalWidth = qrImage.getWidth() / RENDER_SCALE;
        int qrLogicalHeight = qrImage.getHeight() / RENDER_SCALE;
        int qrX = centeredX(qrLogicalWidth, CARD_WIDTH);

        AffineTransform scaledTransform = g.getTransform();
        g.setTransform(new AffineTransform());
        g.drawImage(qrImage, qrX * RENDER_SCALE, Math.round(y * RENDER_SCALE), null);
        g.setTransform(scaledTransform);

        g.setColor(LINE_SOLID);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(qrX, Math.round(y), qrLogicalWidth - 1, qrLogicalHeight - 1);

        return y + qrLogicalHeight;
    }

    // ---- Header --------------------------------------------------------------------------------

    private static float drawHeader(Graphics2D g, ShareCardData data, float y) {
        String label = "CRYPTO UTILS BACKUP SHARE";
        float labelSize = 11f;
        float labelSpacing = spacingFromEm(labelSize, 0.04f);

        drawCenteredText(g, SANS, label, y, CARD_WIDTH, labelSize, labelSpacing, TEXT_SECONDARY_888);
        y += lineHeight(SANS, labelSize);
        y += 2;

        float nameSize = 18f;
        drawCenteredText(g, SANS, data.backupName(), y, CARD_WIDTH, nameSize, 0, TEXT_PRIMARY);
        y += lineHeight(SANS, nameSize);

        return y;
    }

    private static float headerBlockHeight() {
        return lineHeight(SANS, 11f) + 2 + lineHeight(SANS, 18f);
    }

    // ---- Metadata row ---------------------------------------------------------------------------

    private static float drawMetaRow(Graphics2D g, ShareCardData data, float y, int contentWidth) {
        float topY = y;
        float blockHeight = metaBlockHeight();

        g.setColor(LINE_SOLID);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(PADDING_SIDE, Math.round(topY), PADDING_SIDE + contentWidth, Math.round(topY));
        g.drawLine(PADDING_SIDE, Math.round(topY + blockHeight), PADDING_SIDE + contentWidth, Math.round(topY + blockHeight));

        int columnWidth = contentWidth / 3;
        float innerY = topY + 8;

        String shareValue = data.shareIndex() + " / " + data.totalShares();
        drawMetaColumn(g, 0, columnWidth, innerY, "share", shareValue, 18f, 0f);

        String thresholdValue = String.valueOf(data.threshold());
        drawMetaColumn(g, 1, columnWidth, innerY, "threshold", thresholdValue, 18f, 0f);

        String dateValue = data.createdOn().format(DATE_FORMAT);
        drawMetaColumn(g, 2, columnWidth, innerY, "date", dateValue, 18f, 0f);

        g.setColor(LINE_SOLID);
        for (int i = 1; i < 3; i++) {
            int x = PADDING_SIDE + columnWidth * i;
            g.drawLine(x, Math.round(topY), x, Math.round(topY + blockHeight));
        }

        return topY + blockHeight;
    }

    private static void drawMetaColumn(Graphics2D g, int columnIndex, int columnWidth, float y,
                                        String label, String value, float valueSize, float valueGapFromLabel) {
        int columnX = PADDING_SIDE + columnIndex * columnWidth;

        float labelSize = 10f;
        drawCenteredText(g, SANS, label, y, columnWidth, labelSize, 0, TEXT_SECONDARY_888, columnX);

        float valueY = y + lineHeight(SANS, labelSize) + valueGapFromLabel;
        drawCenteredText(g, SANS, value, valueY, columnWidth, valueSize, 0, TEXT_PRIMARY, columnX);
    }

    private static float metaBlockHeight() {
        float labelHeight = lineHeight(SANS, 10f);
        float valueHeight = lineHeight(SANS, 18f);
        return 8 + labelHeight + valueHeight + 8;
    }

    // ---- Hex block ------------------------------------------------------------------------------

    private record HexLayout(List<String> lines, float fontSize, float letterSpacing) {
    }

    private static HexLayout layoutHex(String hex, int maxWidth) {
        List<String> groups = new ArrayList<>();

        for (int i = 0; i < hex.length(); i += HEX_GROUP_SIZE)
            groups.add(hex.substring(i, Math.min(hex.length(), i + HEX_GROUP_SIZE)));

        List<String> lines = new ArrayList<>();

        for (int i = 0; i < groups.size(); i += HEX_GROUPS_PER_LINE) {
            int end = Math.min(groups.size(), i + HEX_GROUPS_PER_LINE);
            lines.add(String.join(" ", groups.subList(i, end)));
        }

        if (lines.isEmpty())
            lines.add("");

        String worstCase = String.join(" ", java.util.Collections.nCopies(HEX_GROUPS_PER_LINE, "0".repeat(HEX_GROUP_SIZE)));

        float fontSize = HEX_FONT_SIZE_DEFAULT;
        float letterSpacing = HEX_LETTER_SPACING_DEFAULT;

        while (stringWidth(MONO, worstCase, fontSize, letterSpacing) > maxWidth) {
            if (fontSize > HEX_FONT_SIZE_MIN) {
                fontSize -= 0.5f;
            } else if (letterSpacing > 0) {
                letterSpacing = Math.max(0, letterSpacing - 0.1f);
            } else {
                break;
            }
        }

        return new HexLayout(lines, fontSize, letterSpacing);
    }

    private static float drawHexBlock(Graphics2D g, HexLayout hexLayout, float y, int contentWidth) {
        float blockHeight = hexBlockHeight(hexLayout);

        g.setColor(LINE_SOLID);
        g.setStroke(new BasicStroke(1f));
        drawRoundRect(g, PADDING_SIDE, Math.round(y), contentWidth, Math.round(blockHeight), 5);

        float innerY = y + HEX_BLOCK_PADDING_V;

        String title = "HEX";
        float titleSize = 9f;
        drawCenteredText(g, SANS, title, innerY, contentWidth, titleSize, spacingFromEm(titleSize, 0.03f), TEXT_SECONDARY_999, PADDING_SIDE);
        innerY += lineHeight(SANS, titleSize) + 5;

        float hexLineHeight = hexLayout.fontSize() * HEX_LINE_HEIGHT_FACTOR;

        for (String line : hexLayout.lines()) {
            drawCenteredText(g, MONO, line, innerY, contentWidth, hexLayout.fontSize(), hexLayout.letterSpacing(), TEXT_PRIMARY, PADDING_SIDE);
            innerY += hexLineHeight;
        }

        return y + blockHeight;
    }

    private static float hexBlockHeight(HexLayout hexLayout) {
        float titleHeight = lineHeight(SANS, 9f) + 5;
        float hexLineHeight = hexLayout.fontSize() * HEX_LINE_HEIGHT_FACTOR;
        float linesHeight = hexLayout.lines().size() * hexLineHeight;

        return HEX_BLOCK_PADDING_V * 2 + titleHeight + linesHeight;
    }

    // ---- Checksum row ---------------------------------------------------------------------------

    private static float drawChecksumRow(Graphics2D g, ShareCardData data, float y, int contentWidth) {
        float fontSize = 9f;
        String left = "SHA-256 checksum: " + data.checksumHex();

        drawText(g, SANS, left, PADDING_SIDE, y + ascent(SANS, fontSize), fontSize, 0, TEXT_SECONDARY_999);

        if (data.typeLabel() != null && !data.typeLabel().isEmpty()) {
            float rightWidth = stringWidth(SANS, data.typeLabel(), fontSize, 0);
            float rightX = PADDING_SIDE + contentWidth - rightWidth;
            drawText(g, SANS, data.typeLabel(), rightX, y + ascent(SANS, fontSize), fontSize, 0, TEXT_SECONDARY_999);
        }

        return y + lineHeight(SANS, fontSize);
    }

    private static float checksumRowHeight() {
        return lineHeight(SANS, 9f);
    }

    // ---- Notes block ----------------------------------------------------------------------------

    private static float drawNotesBlock(Graphics2D g, float y, int contentWidth) {
        g.setColor(LINE_DASHED_NOTES);
        g.setStroke(DASHED_STROKE);
        g.drawLine(PADDING_SIDE, Math.round(y), PADDING_SIDE + contentWidth, Math.round(y));

        float innerY = y + 10;

        String label = "NOTES";
        float labelSize = 9f;
        drawText(g, SANS, label, PADDING_SIDE, innerY + ascent(SANS, labelSize), labelSize, spacingFromEm(labelSize, 0.03f), TEXT_SECONDARY_999);
        innerY += lineHeight(SANS, labelSize) + 6;

        float lineFieldHeight = 16f;
        g.setColor(LINE_SOLID);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(PADDING_SIDE, Math.round(innerY + lineFieldHeight), PADDING_SIDE + contentWidth, Math.round(innerY + lineFieldHeight));
        innerY += lineFieldHeight;

        return innerY;
    }

    private static float notesBlockHeight() {
        return 10 + lineHeight(SANS, 9f) + 6 + 16f;
    }

    // ---- Footer ---------------------------------------------------------------------------------

    private static void drawFooterRow(Graphics2D g, float y, int contentWidth) {
        float fontSize = 9f;

        drawText(g, SANS, "keep this share confidential", PADDING_SIDE, y + ascent(SANS, fontSize), fontSize, 0, TEXT_SECONDARY_999);

        float rightWidth = stringWidth(SANS, REPO_LINK, fontSize, 0);
        float rightX = PADDING_SIDE + contentWidth - rightWidth;
        drawText(g, SANS, REPO_LINK, rightX, y + ascent(SANS, fontSize), fontSize, 0, TEXT_SECONDARY_999);
    }

    private static float footerRowHeight() {
        return lineHeight(SANS, 9f);
    }

    // ---- Canvas sizing --------------------------------------------------------------------------

    private static int computeCanvasHeight(HexLayout hexLayout, BufferedImage qr) {
        float height = PADDING_TOP
                + headerBlockHeight() + 12
                + metaBlockHeight() + 12
                + (qr != null ? qr.getHeight() / RENDER_SCALE + 12 : 0)
                + hexBlockHeight(hexLayout) + 12
                + checksumRowHeight() + 12
                + notesBlockHeight() + 10
                + footerRowHeight()
                + PADDING_BOTTOM;

        return Math.round(height);
    }

    // ---- Text/font helpers ----------------------------------------------------------------------

    private static int centeredX(int contentWidth, int containerWidth) {
        return Math.max(0, (containerWidth - contentWidth) / 2);
    }

    private static float spacingFromEm(float pixelSize, float em) {
        return pixelSize * em;
    }

    private static void drawRoundRect(Graphics2D g, int x, int y, int width, int height, int radius) {
        g.drawRoundRect(x, y, width, height, radius * 2, radius * 2);
    }

    private static void drawCenteredText(Graphics2D g, TrueTypeFont font, String text, float y, int containerWidth,
                                          float pixelSize, float letterSpacing, Color color) {
        drawCenteredText(g, font, text, y, containerWidth, pixelSize, letterSpacing, color, 0);
    }

    private static void drawCenteredText(Graphics2D g, TrueTypeFont font, String text, float y, int containerWidth,
                                          float pixelSize, float letterSpacing, Color color, int containerX) {
        float width = stringWidth(font, text, pixelSize, letterSpacing);
        float x = containerX + centeredX(Math.round(width), containerWidth);
        drawText(g, font, text, x, y + ascent(font, pixelSize), pixelSize, letterSpacing, color);
    }

    private static float scale(TrueTypeFont font, float pixelSize) {
        return pixelSize / (float) font.unitsPerEm();
    }

    private static float ascent(TrueTypeFont font, float pixelSize) {
        return font.ascender() * scale(font, pixelSize);
    }

    private static float lineHeight(TrueTypeFont font, float pixelSize) {
        return (font.ascender() - font.descender()) * scale(font, pixelSize);
    }

    private static float stringWidth(TrueTypeFont font, String text, float pixelSize, float letterSpacing) {
        float scale = scale(font, pixelSize);
        float w = 0;

        for (int i = 0; i < text.length(); i++)
            w += font.advanceWidth(text.charAt(i)) * scale;

        if (text.length() > 1)
            w += (text.length() - 1) * letterSpacing;

        return w;
    }

    private static void drawText(Graphics2D g, TrueTypeFont font, String text, float x, float baselineY,
                                  float pixelSize, float letterSpacing, Color color) {
        float scale = scale(font, pixelSize);
        float cursor = x;

        g.setColor(color);

        for (int i = 0; i < text.length(); i++) {
            GeneralPath glyph = font.glyphPath(text.charAt(i));

            AffineTransform transform = new AffineTransform();
            transform.translate(cursor, baselineY);
            transform.scale(scale, -scale);

            g.fill(transform.createTransformedShape(glyph));

            cursor += font.advanceWidth(text.charAt(i)) * scale + letterSpacing;
        }
    }
}
