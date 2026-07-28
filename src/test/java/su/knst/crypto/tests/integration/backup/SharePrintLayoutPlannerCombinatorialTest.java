package su.knst.crypto.tests.integration.backup;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.CardInput;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.CardTooLargeException;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PageConfig;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.Placement;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PrintPage;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PrintPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Grids SharePrintLayoutPlanner across every realistic card count (1..16, covering a single
 * page through several overflowing pages) and every card-height category a real backup run can
 * produce: small/square, medium with a QR still shown, long with no QR (escalates to a portrait
 * sheet), and too-large-for-any-sheet. A single backup run always produces N cards of identical
 * width/height (equal-length Shamir shares, same QR-or-not decision - see BackupCreateCommand),
 * so testing each category uniformly across every count is the realistic grid, not an arbitrary
 * mix.
 *
 * For the three placeable categories, checks invariants that must hold regardless of exact
 * geometry: every input position produces exactly one placement, no two placements on the same
 * page overlap, and every placement stays within its page bounds. The too-large category checks
 * that the whole plan is rejected (rolled back) instead of a partial one being returned.
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

    @ParameterizedTest(name = "{0} cards, {1}")
    @MethodSource("countsAndCategories")
    void packingInvariantsHoldForEveryCountAndCategory(int cardCount, Category category) {
        List<CardInput> cards = new ArrayList<>();

        for (int i = 0; i < cardCount; i++)
            cards.add(new CardInput(CARD_WIDTH_PX, category.heightPx));

        if (category == Category.TOO_LARGE) {
            assertThrows(SharePrintLayoutPlanner.CardTooLargeException.class,
                    () -> SharePrintLayoutPlanner.plan(cards, PAGE_CONFIG));
            return;
        }

        PrintPlan plan;

        try {
            plan = SharePrintLayoutPlanner.plan(cards, PAGE_CONFIG);
        } catch (CardTooLargeException e) {
            throw new AssertionError("unexpected rollback for category " + category, e);
        }

        assertEquals(cardCount, plan.placements().size());
        assertFalse(cardCount > 0 && plan.pages().isEmpty());

        assertNoOverlapsAndWithinBounds(plan, cards);
    }

    private static void assertNoOverlapsAndWithinBounds(PrintPlan plan, List<CardInput> cards) {
        // {x0, y0, x1, y1} per page index
        List<List<int[]>> rectsByPage = new ArrayList<>();

        for (int i = 0; i < plan.pages().size(); i++)
            rectsByPage.add(new ArrayList<>());

        for (int i = 0; i < plan.placements().size(); i++) {
            Placement placement = plan.placements().get(i);
            CardInput card = cards.get(i);
            PrintPage page = plan.pages().get(placement.pageIndex());

            int x0 = placement.x();
            int y0 = placement.y();
            int x1 = x0 + card.widthPx();
            int y1 = y0 + card.heightPx();

            assertTrue(x0 >= 0 && y0 >= 0 && x1 <= page.widthPx() && y1 <= page.heightPx(),
                    "placement out of page bounds: " + placement + " on " + page.orientation()
                            + " page " + page.widthPx() + "x" + page.heightPx());

            List<int[]> rects = rectsByPage.get(placement.pageIndex());

            for (int[] other : rects)
                assertFalse(overlaps(x0, y0, x1, y1, other[0], other[1], other[2], other[3]),
                        "overlapping placements on the same page at input position " + i);

            rects.add(new int[]{x0, y0, x1, y1});
        }
    }

    private static boolean overlaps(int ax0, int ay0, int ax1, int ay1, int bx0, int by0, int bx1, int by1) {
        return ax0 < bx1 && bx0 < ax1 && ay0 < by1 && by0 < ay1;
    }
}
