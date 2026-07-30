package su.knst.crypto.tests.core.render;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import su.knst.crypto.core.render.CardImage;
import su.knst.crypto.core.render.PngWriter;
import su.knst.crypto.core.render.PrintGeometry;
import su.knst.crypto.core.render.PrintLayoutPlanner;
import su.knst.crypto.core.render.QrCodec;
import su.knst.crypto.core.render.TagImage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PngWriterTest {

    List<Path> filesToCleanUp;

    @BeforeEach
    void setUp() {
        filesToCleanUp = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Path path : filesToCleanUp)
            Files.deleteIfExists(path);
    }

    Path write(BufferedImage image, String name) throws IOException {
        Path path = Path.of(name);
        filesToCleanUp.add(path);

        PngWriter.writeOwnerOnly(image, path);

        return path;
    }

    static BufferedImage card() throws WriterException {
        return CardImage.build(new CardImage.ShareCardData(
                "png_writer_test", 1, 3, 2, LocalDate.of(2026, 7, 30),
                "00112233445566778899AABBCCDDEEFF", "a1b2c3", "FILE",
                ErrorCorrectionLevel.H, true));
    }

    static BufferedImage tag(String name) throws WriterException {
        return TagImage.build(new TagImage.TagData(name, 1, 3, "a1b2c3"));
    }

    static int pixelsPerMeter(Path path) throws IOException {
        String content = Files.readString(path, java.nio.charset.StandardCharsets.ISO_8859_1);

        int chunkIndex = content.indexOf("pHYs");
        assertTrue(chunkIndex >= 0, "pHYs chunk not found in written PNG");

        int dataStart = chunkIndex + 4;

        return ((content.charAt(dataStart) & 0xFF) << 24)
                | ((content.charAt(dataStart + 1) & 0xFF) << 16)
                | ((content.charAt(dataStart + 2) & 0xFF) << 8)
                | (content.charAt(dataStart + 3) & 0xFF);
    }

    @Test
    void cardAndTagAreWrittenAtTheSameDensity() throws Exception {
        Path cardPath = write(card(), "png_writer_card.png");
        Path tagPath = write(tag("Safe, shelf 2"), "png_writer_tag.png");

        assertEquals(PrintGeometry.PIXELS_PER_METER, pixelsPerMeter(cardPath));
        assertEquals(PrintGeometry.PIXELS_PER_METER, pixelsPerMeter(tagPath));
    }

    @Test
    void aCardPrints56mmWide() throws Exception {
        BufferedImage card = card();

        assertEquals(56.0, PrintGeometry.pxToMm(card.getWidth()), 0.1);
    }

    @Test
    void tagHeightIsFixedWhileWidthGrowsWithTheName() throws Exception {
        BufferedImage shortName = tag("A");
        BufferedImage longName = tag("Deposit box at the bank on Main Street, drawer 14");

        assertEquals(shortName.getHeight(), longName.getHeight(), "tag height must not depend on the name");
        assertTrue(longName.getWidth() > shortName.getWidth(), "a longer name must widen the tag");
        assertEquals(25.4, PrintGeometry.pxToMm(shortName.getHeight()), 0.1);
    }

    @Test
    void aPrintSheetComesOutA4() throws Exception {
        BufferedImage card = card();

        PrintLayoutPlanner.PageConfig page = PrintLayoutPlanner.PageConfig.a4FittingCardWidth(card.getWidth());

        assertEquals(210.0, PrintGeometry.pxToMm(page.portraitWidthPx()), 0.5);
        assertEquals(297.0, PrintGeometry.pxToMm(page.portraitHeightPx()), 0.5);
    }

    @Test
    void everyErrorCorrectionLevelHasAReadableName() {
        for (ErrorCorrectionLevel level : QrCodec.LEVELS) {
            String described = QrCodec.describeLevel(level);

            assertTrue(described.matches("\\w+ \\(\\d/4\\)"), described);
        }

        assertTrue(QrCodec.describeLevel(ErrorCorrectionLevel.L).startsWith("Low"));
        assertTrue(QrCodec.describeLevel(ErrorCorrectionLevel.M).startsWith("Medium"));
        assertTrue(QrCodec.describeLevel(ErrorCorrectionLevel.Q).startsWith("Quartile"));
        assertTrue(QrCodec.describeLevel(ErrorCorrectionLevel.H).startsWith("High"));
    }

    @Test
    void decodingAnImageWithNoCodeInItYieldsNothing() {
        BufferedImage blank = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);

        assertNull(QrCodec.decode(blank));
    }

    @Test
    void aCardWithAnEmptyPayloadStillRenders() throws Exception {
        BufferedImage card = CardImage.build(new CardImage.ShareCardData(
                "empty_payload", 1, 1, 1, LocalDate.of(2026, 7, 30),
                "", "a1b2c3", "TEXT", ErrorCorrectionLevel.L, false));

        assertTrue(card.getWidth() > 0);
        assertTrue(card.getHeight() > 0);
    }

    @Test
    void filesAreCreatedOwnerOnly() throws Exception {
        Path path = write(card(), "png_writer_perms.png");

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);

        assertEquals("rw-------", PosixFilePermissions.toString(permissions));
    }
}
