package su.knst.crypto.core.render;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Renders a "container tag" (бирка): a small label meant to be attached to the physical
 * container (box/safe/folder) holding one Shamir share, for fast visual identification from a
 * distance. Unlike {@link CardImage}, a tag carries no QR code and no hex dump of the share
 * - it is not usable to reconstruct the secret, only to identify which container holds which
 * share.
 *
 * Layout is horizontal with three zones left to right: a hole-punch zone, a vertically-oriented
 * checksum barcode, and a text zone. Height is fixed; width grows with the tag's name so the
 * text always fits on one line without wrapping.
 */
public final class TagImage {
    public static final int RENDER_SCALE = 6;

    private static final int TAG_HEIGHT = 75;

    private static final int HOLE_ZONE_WIDTH = 56;
    private static final int GAP_AFTER_HOLE = 8;
    private static final int BARCODE_ZONE_WIDTH = 120;
    private static final int BARCODE_HEIGHT = 32;
    private static final int GAP_AFTER_BARCODE = 8;

    private static final int TEXT_ZONE_PADDING_RIGHT = 16;
    private static final int TEXT_ZONE_MIN_WIDTH = 160;

    private static final float SOLID_STROKE_WIDTH = 1f;

    private static final float TEXT_SIZE = 22f;
    private static final float TEXT_BOLD_STROKE = 1.1f;

    private static final float WATERMARK_SIZE = 10f;
    private static final float WATERMARK_ROTATION_DEG = -18f;
    private static final Color WATERMARK_COLOR = new Color(0xE3, 0xE3, 0xE3);
    private static final String WATERMARK_TEXT = "CRYPTO UTILS BACKUP SHARE";

    private static final Color HATCH_COLOR = new Color(0xF2, 0xF2, 0xF2);
    private static final float HATCH_ROTATION_DEG = 135f;
    private static final int HATCH_PITCH = 6;
    private static final int HATCH_STRIPE_WIDTH = 3;

    public record TagData(String tagName, int shareIndex, int totalShares, String checksumHex) {
    }

    private TagImage() {
    }

    public static BufferedImage build(TagData data) throws WriterException {
        String mainText = data.tagName() + " · " + data.shareIndex() + "/" + data.totalShares();
        float mainTextWidth = GlyphText.stringWidth(GlyphText.SANS, mainText, TEXT_SIZE, 0);

        int textZoneWidth = Math.max(TEXT_ZONE_MIN_WIDTH, Math.round(mainTextWidth) + TEXT_ZONE_PADDING_RIGHT);

        int barcodeZoneX = HOLE_ZONE_WIDTH + GAP_AFTER_HOLE;
        int textZoneX = barcodeZoneX + BARCODE_ZONE_WIDTH + GAP_AFTER_BARCODE;
        int tagWidth = textZoneX + textZoneWidth;

        BufferedImage barcodeImage = buildVerticalBarcodeImage(
                data.checksumHex(), BARCODE_HEIGHT, BARCODE_ZONE_WIDTH);

        BufferedImage canvas = new BufferedImage(
                tagWidth * RENDER_SCALE, TAG_HEIGHT * RENDER_SCALE, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(RENDER_SCALE, RENDER_SCALE);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, tagWidth, TAG_HEIGHT);

        drawHoleZone(g);
        drawWatermark(g, HOLE_ZONE_WIDTH, tagWidth - HOLE_ZONE_WIDTH);
        drawBarcode(g, barcodeImage, barcodeZoneX);
        drawTextZone(g, mainText, textZoneX);

        // Drawn last, on top of the watermark/hatch fills. The outer cut border stays dashed (it
        // marks where to cut/fold), while the hole-zone separator is a solid interior guide line.
        g.setColor(GlyphText.LINE_DASHED_BORDER);
        g.setStroke(GlyphText.DASHED_STROKE);
        g.drawRect(0, 0, tagWidth, TAG_HEIGHT);

        g.setStroke(new BasicStroke(SOLID_STROKE_WIDTH));
        g.drawLine(HOLE_ZONE_WIDTH, 0, HOLE_ZONE_WIDTH, TAG_HEIGHT);

        g.dispose();

        return canvas;
    }

    // ---- Hole zone ------------------------------------------------------------------------------

    private static void drawHoleZone(Graphics2D g) {
        Shape savedClip = g.getClip();
        AffineTransform savedTransform = g.getTransform();

        g.clip(new Rectangle(0, 0, HOLE_ZONE_WIDTH, TAG_HEIGHT));

        g.rotate(Math.toRadians(HATCH_ROTATION_DEG), HOLE_ZONE_WIDTH / 2.0, TAG_HEIGHT / 2.0);

        g.setColor(HATCH_COLOR);

        int span = HOLE_ZONE_WIDTH + TAG_HEIGHT;

        for (int x = -span; x < span; x += HATCH_PITCH)
            g.fillRect(x, -span, HATCH_STRIPE_WIDTH, span * 2);

        g.setTransform(savedTransform);
        g.setClip(savedClip);
    }

    // ---- Barcode --------------------------------------------------------------------------------

    private static BufferedImage buildVerticalBarcodeImage(String checksumHex, int thicknessPx, int lengthPx)
            throws WriterException {
        BitMatrix matrix = new MultiFormatWriter().encode(
                checksumHex,
                BarcodeFormat.CODE_128,
                lengthPx * RENDER_SCALE,
                thicknessPx * RENDER_SCALE,
                Map.of(EncodeHintType.MARGIN, 4)
        );

        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private static void drawBarcode(Graphics2D g, BufferedImage barcodeImage, int zoneX) {
        int logicalWidth = barcodeImage.getWidth() / RENDER_SCALE;
        int logicalHeight = barcodeImage.getHeight() / RENDER_SCALE;

        int x = zoneX + GlyphText.centeredX(logicalWidth, BARCODE_ZONE_WIDTH);
        int y = GlyphText.centeredX(logicalHeight, TAG_HEIGHT);

        // The watermark is painted across the whole hole-to-edge span before this runs, so the
        // barcode needs its own opaque backing - otherwise watermark text would show through its
        // quiet zone around the bars. Only the barcode's own footprint is blanked, not the full
        // zone height, so the watermark still shows above/below it like everywhere else.
        g.setColor(Color.WHITE);
        g.fillRect(x, y, logicalWidth, logicalHeight);

        AffineTransform scaledTransform = g.getTransform();
        g.setTransform(new AffineTransform());
        g.drawImage(barcodeImage, x * RENDER_SCALE, y * RENDER_SCALE, null);
        g.setTransform(scaledTransform);

        g.setColor(GlyphText.LINE_DASHED_BORDER);
        g.setStroke(new BasicStroke(SOLID_STROKE_WIDTH));
        g.drawRect(x, y, logicalWidth, logicalHeight);
    }

    // ---- Text zone ------------------------------------------------------------------------------

    private static void drawTextZone(Graphics2D g, String mainText, int zoneX) {
        // Left-aligned (not centered) so the gap between the barcode's frame and the text matches
        // GAP_AFTER_BARCODE exactly, mirroring the gap between the hole zone and the barcode.
        float baselineY = (TAG_HEIGHT - GlyphText.lineHeight(GlyphText.SANS, TEXT_SIZE)) / 2f
                + GlyphText.ascent(GlyphText.SANS, TEXT_SIZE);

        GlyphText.drawTextBold(g, GlyphText.SANS, mainText, zoneX, baselineY, TEXT_SIZE, 0,
                GlyphText.TEXT_PRIMARY, TEXT_BOLD_STROKE);
    }

    private static void drawWatermark(Graphics2D g, int zoneX, int zoneWidth) {
        Shape savedClip = g.getClip();
        AffineTransform savedTransform = g.getTransform();

        g.clip(new Rectangle(zoneX, 0, zoneWidth, TAG_HEIGHT));
        g.rotate(Math.toRadians(WATERMARK_ROTATION_DEG), zoneX + zoneWidth / 2.0, TAG_HEIGHT / 2.0);

        float watermarkWidth = GlyphText.stringWidth(GlyphText.SANS, WATERMARK_TEXT, WATERMARK_SIZE,
                GlyphText.spacingFromEm(WATERMARK_SIZE, 0.05f));
        float rowPitch = GlyphText.lineHeight(GlyphText.SANS, WATERMARK_SIZE) * 1.8f;
        float colPitch = watermarkWidth + 24f;

        // The zone is rotated in place around its own center, so content well outside its
        // unrotated bounds can rotate into view at the corners - overscan generously in both
        // directions to guarantee full coverage after rotation.
        int span = zoneWidth + TAG_HEIGHT;
        int rows = (int) Math.ceil(span / rowPitch) + 1;
        int cols = (int) Math.ceil(span / colPitch) + 1;

        for (int row = -rows; row <= rows; row++) {
            float rowY = row * rowPitch;
            float xOffset = (row % 2 == 0) ? 0 : colPitch / 2f;

            for (int col = -cols; col <= cols; col++) {
                float x = zoneX + col * colPitch + xOffset;
                GlyphText.drawText(g, GlyphText.SANS, WATERMARK_TEXT, x, rowY, WATERMARK_SIZE,
                        GlyphText.spacingFromEm(WATERMARK_SIZE, 0.05f), WATERMARK_COLOR);
            }
        }

        g.setTransform(savedTransform);
        g.setClip(savedClip);
    }
}
