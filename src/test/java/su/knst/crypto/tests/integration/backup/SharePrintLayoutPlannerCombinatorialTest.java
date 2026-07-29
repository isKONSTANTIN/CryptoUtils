package su.knst.crypto.tests.integration.backup;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.ImageTooLargeException;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PageConfig;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PlacedImage;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PrintPage;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Grids SharePrintLayoutPlanner across every realistic image count (1..16, covering a single
 * page through several overflowing pages) and every image-height category a real backup run can
 * produce: small/square, medium with a QR still shown, long with no QR (escalates to a portrait
 * sheet), and too-large-for-any-sheet. A single backup run always produces N images of identical
 * width/height (equal-length Shamir shares, same QR-or-not decision - see BackupCreateCommand),
 * so testing each category uniformly across every count is the realistic grid, not an arbitrary
 * mix.
 *
 * For the three placeable categories, checks invariants that must hold regardless of exact
 * geometry: every input image ends up placed exactly once, no two placements on the same page
 * overlap, and every placement stays within its page bounds. The too-large category checks that
 * the whole plan is rejected (rolled back) instead of a partial one being returned.
 */
class SharePrintLayoutPlannerCombinatorialTest {

    // Real rendered card width (496 nominal design px * 2x supersample - see ShareCardImage).
    private static final int CARD_WIDTH_PX = 992;
    private static final PageConfig PAGE_CONFIG = PageConfig.a4FittingCardWidth(CARD_WIDTH_PX);

    enum Category {
        SMALL(CARD_WIDTH_PX),
        MEDIUM_WITH_QR(PAGE_CONFIG.landscapeHeightPx() - 260),
        LONG_NO_QR(PAGE_CONFIG.landscapeHeightPx() + 500),
        TOO_LARGE(PAGE_CONFIG.portraitHeightPx() + 500);

        final int heightPx;

        Category(int heightPx) {
            this.heightPx = heightPx;
        }
    }

    static Stream<Arguments> countsAndCategories() {
        List<Arguments> args = new ArrayList<>();

        for (int n = 1; n <= 16; n++)
            for (Category category : Category.values())
                args.add(Arguments.of(n, category));

        return args.stream();
    }

    @ParameterizedTest(name = "{0} images, {1}")
    @MethodSource("countsAndCategories")
    void packingInvariantsHoldForEveryCountAndCategory(int imageCount, Category category) {
        List<BufferedImage> images = new ArrayList<>();

        for (int i = 0; i < imageCount; i++)
            images.add(new BufferedImage(CARD_WIDTH_PX, category.heightPx, BufferedImage.TYPE_INT_RGB));

        if (category == Category.TOO_LARGE) {
            assertThrows(ImageTooLargeException.class,
                    () -> SharePrintLayoutPlanner.plan(images, PAGE_CONFIG));
            return;
        }

        List<PrintPage> plan;

        try {
            plan = SharePrintLayoutPlanner.plan(images, PAGE_CONFIG);
        } catch (ImageTooLargeException e) {
            throw new AssertionError("unexpected rollback for category " + category, e);
        }

        assertEquals(imageCount, totalItems(plan));
        assertFalse(imageCount > 0 && plan.isEmpty());

        assertEachImagePlacedExactlyOnce(plan, images);
        assertNoOverlapsAndWithinBounds(plan);
    }

    private static int totalItems(List<PrintPage> pages) {
        int total = 0;

        for (PrintPage page : pages)
            total += page.items().size();

        return total;
    }

    private static void assertEachImagePlacedExactlyOnce(List<PrintPage> pages, List<BufferedImage> images) {
        for (BufferedImage image : images) {
            long count = pages.stream()
                    .flatMap(p -> p.items().stream())
                    .filter(placed -> placed.image() == image)
                    .count();

            assertEquals(1, count, "expected image to be placed exactly once");
        }
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
                        "placement out of page bounds: " + placed + " on " + page.orientation()
                                + " page " + page.widthPx() + "x" + page.heightPx());

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
