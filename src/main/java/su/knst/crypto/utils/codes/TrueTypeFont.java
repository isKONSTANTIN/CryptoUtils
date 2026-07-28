package su.knst.crypto.utils.codes;

import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal TrueType outline parser/rasterizer. Glyphs are exposed as
 * java.awt.geom.GeneralPath so they can be filled directly with Graphics2D.
 * This avoids java.awt.Font entirely: Font initializes the platform
 * FontManager, which needs a font configuration file that a self-contained
 * GraalVM native-image binary doesn't have.
 *
 * Only simple glyphs and the common cmap formats (0, 4, 6, 12) are supported,
 * which is enough for the ASCII text this tool renders. Composite glyphs
 * (accented letters etc.) resolve to an empty outline.
 */
public final class TrueTypeFont {
    private final ByteBuffer data;
    private final Map<String, int[]> tables = new HashMap<>();
    private final int unitsPerEm;
    private final int ascender;
    private final int descender;
    private final int numGlyphs;
    private final int[] loca;
    private final int glyfOffset;
    private final int[] hmtxAdvances;
    private final Map<Integer, Integer> cmap;
    private final Map<Integer, GeneralPath> glyphCache = new HashMap<>();

    public static TrueTypeFont load(InputStream in) throws IOException {
        return new TrueTypeFont(in.readAllBytes());
    }

    private TrueTypeFont(byte[] bytes) {
        data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);

        int numTables = data.getShort(4) & 0xFFFF;

        for (int i = 0; i < numTables; i++) {
            int base = 12 + i * 16;
            String tag = new String(bytes, base, 4, StandardCharsets.US_ASCII);
            int offset = data.getInt(base + 8);
            int length = data.getInt(base + 12);
            tables.put(tag, new int[]{offset, length});
        }

        int[] head = require("head");
        unitsPerEm = data.getShort(head[0] + 18) & 0xFFFF;
        int indexToLocFormat = data.getShort(head[0] + 50);

        int[] maxp = require("maxp");
        numGlyphs = data.getShort(maxp[0] + 4) & 0xFFFF;

        int[] hhea = require("hhea");
        ascender = data.getShort(hhea[0] + 4);
        descender = data.getShort(hhea[0] + 6);
        int numberOfHMetrics = data.getShort(hhea[0] + 34) & 0xFFFF;

        int[] hmtx = require("hmtx");
        hmtxAdvances = parseHmtx(hmtx[0], numberOfHMetrics, numGlyphs);

        int[] glyfTable = require("glyf");
        glyfOffset = glyfTable[0];
        loca = parseLoca(require("loca")[0], indexToLocFormat);

        cmap = parseCmap(require("cmap")[0]);
    }

    private int[] require(String tag) {
        int[] entry = tables.get(tag);

        if (entry == null)
            throw new IllegalStateException("Font is missing required table: " + tag);

        return entry;
    }

    public int unitsPerEm() {
        return unitsPerEm;
    }

    public int ascender() {
        return ascender;
    }

    public int descender() {
        return descender;
    }

    public int advanceWidth(char c) {
        Integer glyphId = cmap.get((int) c);

        if (glyphId == null || glyphId < 0 || glyphId >= hmtxAdvances.length)
            return hmtxAdvances.length > 0 ? hmtxAdvances[hmtxAdvances.length - 1] : 0;

        return hmtxAdvances[glyphId];
    }

    public GeneralPath glyphPath(char c) {
        Integer glyphId = cmap.get((int) c);

        if (glyphId == null)
            return new GeneralPath();

        return glyphCache.computeIfAbsent(glyphId, this::parseGlyph);
    }

    // OpenType rule: hmtx stores an (advanceWidth, lsb) pair for the first numberOfHMetrics
    // glyphs; every glyph after that has no advanceWidth entry of its own and reuses the last one.
    private int[] parseHmtx(int offset, int numberOfHMetrics, int numGlyphs) {
        int[] advances = new int[numGlyphs];
        int last = 0;
        int pos = offset;

        for (int i = 0; i < numGlyphs; i++) {
            if (i < numberOfHMetrics) {
                last = data.getShort(pos) & 0xFFFF;
                pos += 4;
            }

            advances[i] = last;
        }

        return advances;
    }

    private int[] parseLoca(int offset, int format) {
        int[] result = new int[numGlyphs + 1];

        if (format == 0) {
            for (int i = 0; i <= numGlyphs; i++)
                result[i] = (data.getShort(offset + i * 2) & 0xFFFF) * 2;
        } else {
            for (int i = 0; i <= numGlyphs; i++)
                result[i] = data.getInt(offset + i * 4);
        }

        return result;
    }

    private Map<Integer, Integer> parseCmap(int cmapOffset) {
        int numSubtables = data.getShort(cmapOffset + 2) & 0xFFFF;
        int bestOffset = -1;
        int bestScore = -1;

        for (int i = 0; i < numSubtables; i++) {
            int recordBase = cmapOffset + 4 + i * 8;
            int platformId = data.getShort(recordBase) & 0xFFFF;
            int encodingId = data.getShort(recordBase + 2) & 0xFFFF;
            int subtableOffset = data.getInt(recordBase + 4);

            int score;
            if (platformId == 3 && encodingId == 1) score = 3;
            else if (platformId == 0) score = 2;
            else if (platformId == 3 && encodingId == 0) score = 1;
            else if (platformId == 1 && encodingId == 0) score = 0;
            else score = -1;

            if (score > bestScore) {
                bestScore = score;
                bestOffset = cmapOffset + subtableOffset;
            }
        }

        if (bestOffset < 0)
            throw new IllegalStateException("No usable cmap subtable found");

        int format = data.getShort(bestOffset) & 0xFFFF;
        Map<Integer, Integer> result = new HashMap<>();

        switch (format) {
            case 0 -> parseCmapFormat0(bestOffset, result);
            case 4 -> parseCmapFormat4(bestOffset, result);
            case 6 -> parseCmapFormat6(bestOffset, result);
            case 12 -> parseCmapFormat12(bestOffset, result);
            default -> throw new IllegalStateException("Unsupported cmap format: " + format);
        }

        return result;
    }

    private void parseCmapFormat0(int offset, Map<Integer, Integer> result) {
        for (int c = 0; c < 256; c++) {
            int glyphId = data.get(offset + 6 + c) & 0xFF;

            if (glyphId != 0)
                result.put(c, glyphId);
        }
    }

    private void parseCmapFormat6(int offset, Map<Integer, Integer> result) {
        int first = data.getShort(offset + 6) & 0xFFFF;
        int count = data.getShort(offset + 8) & 0xFFFF;

        for (int i = 0; i < count; i++) {
            int glyphId = data.getShort(offset + 10 + i * 2) & 0xFFFF;

            if (glyphId != 0)
                result.put(first + i, glyphId);
        }
    }

    private void parseCmapFormat4(int offset, Map<Integer, Integer> result) {
        int segCountX2 = data.getShort(offset + 6) & 0xFFFF;
        int segCount = segCountX2 / 2;

        int endCodesOffset = offset + 14;
        int startCodesOffset = endCodesOffset + segCountX2 + 2;
        int idDeltaOffset = startCodesOffset + segCountX2;
        int idRangeOffsetOffset = idDeltaOffset + segCountX2;

        for (int seg = 0; seg < segCount; seg++) {
            int endCode = data.getShort(endCodesOffset + seg * 2) & 0xFFFF;
            int startCode = data.getShort(startCodesOffset + seg * 2) & 0xFFFF;
            int idDelta = data.getShort(idDeltaOffset + seg * 2);
            int idRangeOffset = data.getShort(idRangeOffsetOffset + seg * 2) & 0xFFFF;

            if (startCode == 0xFFFF && endCode == 0xFFFF)
                continue;

            for (int c = startCode; c <= endCode && c != 0xFFFF; c++) {
                int glyphId;

                if (idRangeOffset == 0) {
                    glyphId = (c + idDelta) & 0xFFFF;
                } else {
                    int glyphIndexAddress = idRangeOffsetOffset + seg * 2 + idRangeOffset + (c - startCode) * 2;
                    int rawGlyphId = data.getShort(glyphIndexAddress) & 0xFFFF;
                    glyphId = rawGlyphId == 0 ? 0 : (rawGlyphId + idDelta) & 0xFFFF;
                }

                if (glyphId != 0)
                    result.put(c, glyphId);
            }
        }
    }

    private void parseCmapFormat12(int offset, Map<Integer, Integer> result) {
        long numGroups = data.getInt(offset + 12) & 0xFFFFFFFFL;

        for (long g = 0; g < numGroups; g++) {
            int base = (int) (offset + 16 + g * 12);
            long startChar = data.getInt(base) & 0xFFFFFFFFL;
            long endChar = data.getInt(base + 4) & 0xFFFFFFFFL;
            long startGlyphId = data.getInt(base + 8) & 0xFFFFFFFFL;

            for (long c = startChar; c <= endChar; c++)
                result.put((int) c, (int) (startGlyphId + (c - startChar)));
        }
    }

    private GeneralPath parseGlyph(int glyphId) {
        if (glyphId < 0 || glyphId >= numGlyphs)
            return new GeneralPath();

        int start = loca[glyphId];
        int end = loca[glyphId + 1];

        if (end <= start)
            return new GeneralPath();

        int offset = glyfOffset + start;
        int numberOfContours = data.getShort(offset);

        if (numberOfContours < 0)
            return new GeneralPath();

        return parseSimpleGlyph(offset, numberOfContours);
    }

    private GeneralPath parseSimpleGlyph(int offset, int numberOfContours) {
        int pos = offset + 10;

        int[] endPts = new int[numberOfContours];
        for (int i = 0; i < numberOfContours; i++) {
            endPts[i] = data.getShort(pos) & 0xFFFF;
            pos += 2;
        }

        int numPoints = numberOfContours == 0 ? 0 : endPts[numberOfContours - 1] + 1;

        int instructionLength = data.getShort(pos) & 0xFFFF;
        pos += 2 + instructionLength;

        int[] flags = new int[numPoints];
        for (int i = 0; i < numPoints; ) {
            int flag = data.get(pos++) & 0xFF;
            flags[i++] = flag;

            if ((flag & 0x08) != 0) {
                int repeat = data.get(pos++) & 0xFF;

                for (int r = 0; r < repeat && i < numPoints; r++)
                    flags[i++] = flag;
            }
        }

        int[] xs = new int[numPoints];
        int x = 0;
        for (int i = 0; i < numPoints; i++) {
            int flag = flags[i];

            if ((flag & 0x02) != 0) {
                int dx = data.get(pos++) & 0xFF;
                x += (flag & 0x10) != 0 ? dx : -dx;
            } else if ((flag & 0x10) == 0) {
                x += data.getShort(pos);
                pos += 2;
            }

            xs[i] = x;
        }

        int[] ys = new int[numPoints];
        int y = 0;
        for (int i = 0; i < numPoints; i++) {
            int flag = flags[i];

            if ((flag & 0x04) != 0) {
                int dy = data.get(pos++) & 0xFF;
                y += (flag & 0x20) != 0 ? dy : -dy;
            } else if ((flag & 0x20) == 0) {
                y += data.getShort(pos);
                pos += 2;
            }

            ys[i] = y;
        }

        GeneralPath path = new GeneralPath(Path2D.WIND_NON_ZERO);
        int pointStart = 0;

        for (int c = 0; c < numberOfContours; c++) {
            int pointEnd = endPts[c];
            buildContour(path, flags, xs, ys, pointStart, pointEnd);
            pointStart = pointEnd + 1;
        }

        return path;
    }

    private void buildContour(GeneralPath path, int[] flags, int[] xs, int[] ys, int start, int end) {
        int n = end - start + 1;

        if (n <= 0)
            return;

        int startIndex = -1;
        for (int i = 0; i < n; i++) {
            if ((flags[start + i] & 0x01) != 0) {
                startIndex = i;
                break;
            }
        }

        float startX;
        float startY;

        if (startIndex == -1) {
            startX = (xs[start] + xs[start + 1 % n]) / 2f;
            startY = (ys[start] + ys[start + 1 % n]) / 2f;
            startIndex = 0;
        } else {
            startX = xs[start + startIndex];
            startY = ys[start + startIndex];
        }

        path.moveTo(startX, startY);

        Float pendingCtrlX = null;
        Float pendingCtrlY = null;

        for (int step = 1; step <= n; step++) {
            int i = (startIndex + step) % n;
            boolean onCurve = (flags[start + i] & 0x01) != 0;
            float px = xs[start + i];
            float py = ys[start + i];

            if (onCurve) {
                if (pendingCtrlX != null) {
                    path.quadTo(pendingCtrlX, pendingCtrlY, px, py);
                    pendingCtrlX = null;
                } else {
                    path.lineTo(px, py);
                }
            } else {
                if (pendingCtrlX != null) {
                    float midX = (pendingCtrlX + px) / 2f;
                    float midY = (pendingCtrlY + py) / 2f;
                    path.quadTo(pendingCtrlX, pendingCtrlY, midX, midY);
                }

                pendingCtrlX = px;
                pendingCtrlY = py;
            }
        }

        if (pendingCtrlX != null)
            path.quadTo(pendingCtrlX, pendingCtrlY, startX, startY);

        path.closePath();
    }
}
