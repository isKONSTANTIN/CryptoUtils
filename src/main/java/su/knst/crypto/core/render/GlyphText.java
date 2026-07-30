package su.knst.crypto.core.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Shared TrueTypeFont-based text rendering helpers and the palette/dash style used by both
 * {@link CardImage} and {@link TagImage}, so the two printable artifact types share one
 * visual family without duplicating the glyph-layout math twice.
 */
public final class GlyphText {
    public static final Color TEXT_PRIMARY = new Color(0x11, 0x11, 0x11);
    public static final Color TEXT_SECONDARY_888 = new Color(0x88, 0x88, 0x88);
    public static final Color TEXT_SECONDARY_999 = new Color(0x99, 0x99, 0x99);
    public static final Color LINE_SOLID = new Color(0xDD, 0xDD, 0xDD);
    public static final Color LINE_DASHED_BORDER = new Color(0x99, 0x99, 0x99);
    public static final Color LINE_DASHED_NOTES = new Color(0xBB, 0xBB, 0xBB);

    public static final BasicStroke DASHED_STROKE =
            new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f);

    public static final TrueTypeFont SANS = loadFont("Roboto.ttf");
    public static final TrueTypeFont MONO = loadFont("RobotoMono.ttf");

    private GlyphText() {
    }

    public static TrueTypeFont loadFont(String fileName) {
        try (InputStream in = GlyphText.class.getResourceAsStream("/su/knst/crypto/fonts/" + fileName)) {
            return TrueTypeFont.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static int centeredX(int contentWidth, int containerWidth) {
        return Math.max(0, (containerWidth - contentWidth) / 2);
    }

    public static float spacingFromEm(float pixelSize, float em) {
        return pixelSize * em;
    }

    public static float scale(TrueTypeFont font, float pixelSize) {
        return pixelSize / (float) font.unitsPerEm();
    }

    public static float ascent(TrueTypeFont font, float pixelSize) {
        return font.ascender() * scale(font, pixelSize);
    }

    public static float lineHeight(TrueTypeFont font, float pixelSize) {
        return (font.ascender() - font.descender()) * scale(font, pixelSize);
    }

    public static float stringWidth(TrueTypeFont font, String text, float pixelSize, float letterSpacing) {
        float scale = scale(font, pixelSize);
        float w = 0;

        for (int i = 0; i < text.length(); i++)
            w += font.advanceWidth(text.charAt(i)) * scale;

        if (text.length() > 1)
            w += (text.length() - 1) * letterSpacing;

        return w;
    }

    public static void drawText(Graphics2D g, TrueTypeFont font, String text, float x, float baselineY,
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

    // Regular Roboto is the only weight bundled (no bold .ttf ships with this tool, since
    // java.awt.Font/platform fonts aren't usable from a font-config-less GraalVM native image
    // build - see TrueTypeFont's class doc). Faux bold is achieved by stroking each glyph
    // outline on top of the normal fill, which thickens stems/curves without needing a second
    // font file.
    public static void drawTextBold(Graphics2D g, TrueTypeFont font, String text, float x, float baselineY,
                                     float pixelSize, float letterSpacing, Color color, float boldStrokeWidth) {
        float scale = scale(font, pixelSize);
        float cursor = x;

        g.setColor(color);

        Stroke savedStroke = g.getStroke();
        g.setStroke(new BasicStroke(boldStrokeWidth));

        for (int i = 0; i < text.length(); i++) {
            GeneralPath glyph = font.glyphPath(text.charAt(i));

            AffineTransform transform = new AffineTransform();
            transform.translate(cursor, baselineY);
            transform.scale(scale, -scale);

            Shape transformed = transform.createTransformedShape(glyph);
            g.fill(transformed);
            g.draw(transformed);

            cursor += font.advanceWidth(text.charAt(i)) * scale + letterSpacing;
        }

        g.setStroke(savedStroke);
    }

    public static void drawCenteredText(Graphics2D g, TrueTypeFont font, String text, float y, int containerWidth,
                                         float pixelSize, float letterSpacing, Color color) {
        drawCenteredText(g, font, text, y, containerWidth, pixelSize, letterSpacing, color, 0);
    }

    public static void drawCenteredText(Graphics2D g, TrueTypeFont font, String text, float y, int containerWidth,
                                         float pixelSize, float letterSpacing, Color color, int containerX) {
        float width = stringWidth(font, text, pixelSize, letterSpacing);
        float x = containerX + centeredX(Math.round(width), containerWidth);
        drawText(g, font, text, x, y + ascent(font, pixelSize), pixelSize, letterSpacing, color);
    }

    public static void drawCenteredTextBold(Graphics2D g, TrueTypeFont font, String text, float y, int containerWidth,
                                             float pixelSize, float letterSpacing, Color color, float boldStrokeWidth,
                                             int containerX) {
        float width = stringWidth(font, text, pixelSize, letterSpacing);
        float x = containerX + centeredX(Math.round(width), containerWidth);
        drawTextBold(g, font, text, x, y + ascent(font, pixelSize), pixelSize, letterSpacing, color, boldStrokeWidth);
    }
}
