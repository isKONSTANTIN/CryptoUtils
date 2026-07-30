package su.knst.crypto.tests.integration.backup;

import org.junit.jupiter.api.Test;
import su.knst.crypto.core.render.PrintLayoutPlanner;
import su.knst.crypto.core.render.PrintLayoutPlanner.ImageTooLargeException;
import su.knst.crypto.core.render.PrintLayoutPlanner.Orientation;
import su.knst.crypto.core.render.PrintLayoutPlanner.PageConfig;
import su.knst.crypto.core.render.PrintLayoutPlanner.PlacedImage;
import su.knst.crypto.core.render.PrintLayoutPlanner.PrintPage;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SharePrintLayoutPlannerTest {

    // Small human-checkable geometry: portrait 500x1000 -> landscape is 1000x500.
    private static final PageConfig TEST_CONFIG = new PageConfig(500, 1000);
    private static final int CARD_WIDTH = 100;

    private static BufferedImage image(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    private static List<BufferedImage> images(int... heights) {
        List<BufferedImage> images = new ArrayList<>();

        for (int height : heights)
            images.add(image(CARD_WIDTH, height));

        return images;
    }

    private static List<PrintPage> plan(List<BufferedImage> images) throws ImageTooLargeException {
        return PrintLayoutPlanner.plan(images, TEST_CONFIG);
    }

    private static int totalItems(List<PrintPage> pages) {
        int total = 0;

        for (PrintPage page : pages)
            total += page.items().size();

        return total;
    }

    @Test
    void zeroImagesProducesEmptyPlan() throws ImageTooLargeException {
        List<PrintPage> plan = plan(List.of());

        assertTrue(plan.isEmpty());
    }

    @Test
    void shortImagesGoOnALandscapePageByDefault() throws ImageTooLargeException {
        List<PrintPage> plan = plan(images(50, 50, 50));

        assertEquals(1, plan.size());
        assertEquals(Orientation.LANDSCAPE, plan.get(0).orientation());
        assertEquals(TEST_CONFIG.landscapeWidthPx(), plan.get(0).widthPx());
        assertEquals(TEST_CONFIG.landscapeHeightPx(), plan.get(0).heightPx());
    }

    @Test
    void imagesPackLeftToRightIntoAShelfThenWrapToANewShelfBelow() throws ImageTooLargeException {
        // landscape page is 1000 wide; 100px-wide images -> 10 fit in one shelf before wrapping.
        int[] heights = new int[11];
        java.util.Arrays.fill(heights, 50);

        List<PrintPage> plan = plan(images(heights));

        assertEquals(1, plan.size());

        List<PlacedImage> items = plan.get(0).items();
        assertEquals(11, items.size());

        // all same height -> sort is stable, input order preserved within the shelf pass.
        for (int i = 0; i < 10; i++) {
            PlacedImage placed = items.get(i);
            assertEquals(i * CARD_WIDTH, placed.x());
            assertEquals(0, placed.y());
        }

        // 11th image wraps to a new shelf below the first (shelf height was 50).
        PlacedImage last = items.get(10);
        assertEquals(0, last.x());
        assertEquals(50, last.y());
    }

    @Test
    void tallImageEscalatesToPortraitPage() throws ImageTooLargeException {
        // taller than landscape's 500px budget, but fits portrait's 1000px.
        List<PrintPage> plan = plan(images(600));

        assertEquals(1, plan.size());

        PrintPage page = plan.get(0);
        assertEquals(Orientation.PORTRAIT, page.orientation());
        assertEquals(TEST_CONFIG.portraitWidthPx(), page.widthPx());
        assertEquals(TEST_CONFIG.portraitHeightPx(), page.heightPx());

        PlacedImage placed = page.items().get(0);
        assertEquals(0, placed.x());
        assertEquals(0, placed.y());
    }

    @Test
    void imageExactlyAtLandscapeHeightBoundaryStillUsesLandscape() throws ImageTooLargeException {
        List<PrintPage> plan = plan(images(500));

        assertEquals(Orientation.LANDSCAPE, plan.get(0).orientation());
    }

    @Test
    void imageExactlyAtPortraitHeightBoundaryFits() throws ImageTooLargeException {
        List<PrintPage> plan = plan(images(1000));

        assertEquals(1, plan.size());
        assertEquals(Orientation.PORTRAIT, plan.get(0).orientation());
    }

    @Test
    void imageTooTallForEitherOrientationRollsBackWithNoPlanAtAll() {
        List<BufferedImage> input = List.of(image(CARD_WIDTH, 50), image(CARD_WIDTH, 1001));

        ImageTooLargeException ex = assertThrows(ImageTooLargeException.class, () -> plan(input));

        assertTrue(ex.getMessage().contains("1"));
    }

    @Test
    void imageTooWideForEitherOrientationRollsBack() {
        // width 1001 > portrait width (500) and > landscape width (1000).
        List<BufferedImage> input = List.of(image(1001, 50));

        assertThrows(ImageTooLargeException.class, () -> plan(input));
    }

    @Test
    void widthFitsLandscapeButNotPortraitAndHeightForcesPortraitIsTooLarge() {
        // width 600 fits landscape's 1000 width but not portrait's 500 width; height 600
        // doesn't fit landscape's 500 height -> needs portrait, but portrait width is too small.
        List<BufferedImage> input = List.of(image(600, 600));

        assertThrows(ImageTooLargeException.class, () -> plan(input));
    }

    @Test
    void bestFitReusesAnEarlierShelfsLeftoverWidthInsteadOfAlwaysUsingTheNewestShelf() throws ImageTooLargeException {
        // Sorted tallest-first: 80 tall (width 900, opens shelf A with 100px left), then 50 tall
        // (width 900, doesn't fit shelf A's 100px remaining -> opens shelf B below with 100px
        // left), then a 90-wide image that fits both shelf A and B's leftover 100px - best-fit
        // must pick the shelf wasting the least width, i.e. whichever leaves less slack. Both
        // leave 10px slack here, so make them differ: shelf A leftover 100, shelf B leftover 100
        // is ambiguous, so use a case where B's remainder is trimmed further first.
        BufferedImage tall = image(900, 80);
        BufferedImage medium = image(850, 50);
        BufferedImage small = image(90, 10);

        // tall -> shelf A (y=0, height=80, remainingWidth=100)
        // medium -> doesn't fit shelf A (100 < 850) -> shelf B (y=80, height=50, remainingWidth=150)
        // small (width 90) -> fits shelf A (remainingWidth 100, waste 10) and shelf B
        //   (remainingWidth 150, waste 60) -> best fit picks shelf A.
        List<PrintPage> plan = plan(List.of(tall, medium, small));

        assertEquals(1, plan.size());

        List<PlacedImage> items = plan.get(0).items();
        assertEquals(3, items.size());

        PlacedImage placedSmall = items.stream().filter(p -> p.image() == small).findFirst().orElseThrow();

        assertEquals(900, placedSmall.x());
        assertEquals(0, placedSmall.y());
    }

    @Test
    void placementsCoverEveryInputImageExactlyOnce() throws ImageTooLargeException {
        List<BufferedImage> input = images(50, 50, 50);
        List<PrintPage> plan = plan(input);

        assertEquals(3, totalItems(plan));

        for (BufferedImage img : input) {
            long count = plan.stream()
                    .flatMap(p -> p.items().stream())
                    .filter(placed -> placed.image() == img)
                    .count();

            assertEquals(1, count);
        }
    }

    @Test
    void mixedWidthItemsPackWithoutOverlap() throws ImageTooLargeException {
        // a wide "label" mixed in with narrower images, all on one landscape page (width 1000).
        List<BufferedImage> input = List.of(
                image(100, 50),
                image(400, 60),
                image(100, 50));
        List<PrintPage> plan = plan(input);

        assertEquals(1, plan.size());
        assertNoOverlapsAndWithinBounds(plan);
    }

    @Test
    void realA4ConfigDerivedFromActualCardPixelWidth() throws ImageTooLargeException {
        int cardWidth = 1052; // real rendered card width (526 nominal design px * 2x supersample)
        PageConfig a4 = PageConfig.a4FittingCardWidth(cardWidth);

        // page size is derived from the card's rendered width treated as 59.4mm (see
        // PageConfig.a4FittingCardWidth), so a row fits 210/59.4 ~= 3.54 card widths and a column
        // fits exactly 297/59.4 = 5 of them.
        assertEquals(3719, a4.portraitWidthPx());
        assertEquals(5260, a4.landscapeWidthPx());
        assertEquals(5 * cardWidth, a4.landscapeWidthPx());

        List<PrintPage> plan = PrintLayoutPlanner.plan(
                List.of(image(1052, 1200), image(1052, 1200)), a4);

        assertEquals(1, plan.size());
        assertEquals(Orientation.LANDSCAPE, plan.get(0).orientation());
    }

    private static void assertNoOverlapsAndWithinBounds(List<PrintPage> pages) {
        for (PrintPage page : pages) {
            List<int[]> rects = new ArrayList<>();

            for (PlacedImage placed : page.items()) {
                int x0 = placed.x();
                int y0 = placed.y();
                int x1 = x0 + placed.image().getWidth();
                int y1 = y0 + placed.image().getHeight();

                assertTrue(x0 >= 0 && y0 >= 0 && x1 <= page.widthPx() && y1 <= page.heightPx(),
                        "placement out of page bounds: " + placed);

                for (int[] other : rects)
                    assertFalse(overlaps(x0, y0, x1, y1, other[0], other[1], other[2], other[3]),
                            "overlapping placements on the same page");

                rects.add(new int[]{x0, y0, x1, y1});
            }
        }
    }

    private static boolean overlaps(int ax0, int ay0, int ax1, int ay1, int bx0, int by0, int bx1, int by1) {
        return ax0 < bx1 && bx0 < ax1 && ay0 < by1 && by0 < ay1;
    }
}
