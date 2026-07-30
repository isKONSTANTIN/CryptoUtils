package su.knst.crypto.tests.core.render;

import org.junit.jupiter.api.Test;
import su.knst.crypto.core.render.PrintGeometry;

import static org.junit.jupiter.api.Assertions.*;

class PrintGeometryTest {

    @Test
    void millimetresAndPixelsAreInverses() {
        assertEquals(59.4, PrintGeometry.pxToMm(PrintGeometry.mmToPx(59.4)), 0.05);
        assertEquals(25.4, PrintGeometry.pxToMm(PrintGeometry.mmToPx(25.4)), 0.05);
        assertEquals(210.0, PrintGeometry.pxToMm(PrintGeometry.mmToPx(210.0)), 0.05);
    }

    @Test
    void theDensityMatchesWhatIsWrittenIntoEveryPng() {
        assertEquals(PrintGeometry.PIXELS_PER_METER, Math.round(PrintGeometry.PX_PER_MM * 1000));
    }

    @Test
    void aCardIsOneFifthOfALandscapeA4Wide() {
        // 526 logical px at the card's 2x supersampling
        assertEquals(59.4, PrintGeometry.pxToMm(1052), 0.05);
        assertEquals(297.0 / 5.0, PrintGeometry.CARD_WIDTH_MM, 0.001);
    }

    @Test
    void zeroStaysZero() {
        assertEquals(0, PrintGeometry.mmToPx(0));
        assertEquals(0.0, PrintGeometry.pxToMm(0), 0.001);
    }
}
