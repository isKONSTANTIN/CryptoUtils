package su.knst.crypto.tests.integration.backup;

import org.junit.jupiter.api.Test;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.CardInput;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.CardTooLargeException;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.Orientation;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PageConfig;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.Placement;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PrintPage;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PrintPlan;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SharePrintLayoutPlannerTest {

    // Small human-checkable geometry: portrait 500x1000 -> landscape is 1000x500.
    private static final PageConfig TEST_CONFIG = new PageConfig(500, 1000);
    private static final int CARD_WIDTH = 100;

    private static List<CardInput> cards(int... heights) {
        List<CardInput> cards = new ArrayList<>();

        for (int height : heights)
            cards.add(new CardInput(CARD_WIDTH, height));

        return cards;
    }

    private static PrintPlan plan(List<CardInput> cards) throws CardTooLargeException {
        return SharePrintLayoutPlanner.plan(cards, TEST_CONFIG);
    }

    @Test
    void zeroCardsProducesEmptyPlan() throws CardTooLargeException {
        PrintPlan plan = plan(List.of());

        assertTrue(plan.pages().isEmpty());
        assertTrue(plan.placements().isEmpty());
    }

    @Test
    void shortCardsGoOnALandscapePageByDefault() throws CardTooLargeException {
        PrintPlan plan = plan(cards(50, 50, 50));

        assertEquals(1, plan.pages().size());
        assertEquals(Orientation.LANDSCAPE, plan.pages().get(0).orientation());
        assertEquals(TEST_CONFIG.landscapeWidthPx(), plan.pages().get(0).widthPx());
        assertEquals(TEST_CONFIG.landscapeHeightPx(), plan.pages().get(0).heightPx());
    }

    @Test
    void cardsPackLeftToRightIntoAShelfThenWrapToANewShelfBelow() throws CardTooLargeException {
        // landscape page is 1000 wide; 100px-wide cards -> 10 fit in one shelf before wrapping.
        int[] heights = new int[11];
        java.util.Arrays.fill(heights, 50);

        PrintPlan plan = plan(cards(heights));

        assertEquals(1, plan.pages().size());
        assertEquals(11, plan.placements().size());

        for (int i = 0; i < 10; i++) {
            Placement placement = plan.placements().get(i);
            assertEquals(0, placement.pageIndex());
            assertEquals(i * CARD_WIDTH, placement.x());
            assertEquals(0, placement.y());
        }

        // 11th card wraps to a new shelf below the first (shelf height was 50).
        Placement last = plan.placements().get(10);
        assertEquals(0, last.x());
        assertEquals(50, last.y());
    }

    @Test
    void shelfHeightGrowsToFitTheTallestItemPlacedInIt() throws CardTooLargeException {
        // first item sets shelf height 50, second (still fitting width-wise) is taller (80) and
        // grows the shelf; a third item must then start below at y=80, not y=50.
        List<CardInput> input = List.of(
                new CardInput(100, 50),
                new CardInput(100, 80),
                new CardInput(1000, 30)); // full width -> forces a new shelf regardless
        PrintPlan plan = plan(input);

        assertEquals(1, plan.pages().size());

        Placement third = plan.placements().get(2);
        assertEquals(0, third.x());
        assertEquals(80, third.y());
    }

    @Test
    void tallCardEscalatesToPortraitPage() throws CardTooLargeException {
        // taller than landscape's 500px budget, but fits portrait's 1000px.
        PrintPlan plan = plan(cards(600));

        assertEquals(1, plan.pages().size());

        PrintPage page = plan.pages().get(0);
        assertEquals(Orientation.PORTRAIT, page.orientation());
        assertEquals(TEST_CONFIG.portraitWidthPx(), page.widthPx());
        assertEquals(TEST_CONFIG.portraitHeightPx(), page.heightPx());

        Placement placement = plan.placements().get(0);
        assertEquals(0, placement.x());
        assertEquals(0, placement.y());
    }

    @Test
    void cardExactlyAtLandscapeHeightBoundaryStillUsesLandscape() throws CardTooLargeException {
        PrintPlan plan = plan(cards(500));

        assertEquals(Orientation.LANDSCAPE, plan.pages().get(0).orientation());
    }

    @Test
    void cardExactlyAtPortraitHeightBoundaryFits() throws CardTooLargeException {
        PrintPlan plan = plan(cards(1000));

        assertEquals(1, plan.pages().size());
        assertEquals(Orientation.PORTRAIT, plan.pages().get(0).orientation());
    }

    @Test
    void cardTooTallForEitherOrientationRollsBackWithNoPlanAtAll() {
        List<CardInput> input = List.of(new CardInput(CARD_WIDTH, 50), new CardInput(CARD_WIDTH, 1001));

        CardTooLargeException ex = assertThrows(CardTooLargeException.class, () -> plan(input));

        assertTrue(ex.getMessage().contains("1"));
    }

    @Test
    void cardTooWideForEitherOrientationRollsBack() {
        // width 1001 > portrait width (500) and > landscape width (1000).
        List<CardInput> input = List.of(new CardInput(1001, 50));

        assertThrows(CardTooLargeException.class, () -> plan(input));
    }

    @Test
    void widthFitsLandscapeButNotPortraitAndHeightForcesPortraitIsTooLarge() {
        // width 600 fits landscape's 1000 width but not portrait's 500 width; height 600
        // doesn't fit landscape's 500 height -> needs portrait, but portrait width is too small.
        List<CardInput> input = List.of(new CardInput(600, 600));

        assertThrows(CardTooLargeException.class, () -> plan(input));
    }

    @Test
    void placementsArePositionalAndFollowInputOrder() throws CardTooLargeException {
        PrintPlan plan = plan(cards(50, 50, 50));

        assertEquals(3, plan.placements().size());

        for (int i = 0; i < 3; i++)
            assertEquals(i * CARD_WIDTH, plan.placements().get(i).x());
    }

    @Test
    void mixedWidthItemsPackWithoutOverlap() throws CardTooLargeException {
        // a wide "label" mixed in with narrower cards, all on one landscape page (width 1000).
        List<CardInput> input = List.of(
                new CardInput(100, 50),
                new CardInput(400, 60),
                new CardInput(100, 50));
        PrintPlan plan = plan(input);

        assertEquals(1, plan.pages().size());

        Placement p0 = plan.placements().get(0);
        Placement p1 = plan.placements().get(1);
        Placement p2 = plan.placements().get(2);

        assertEquals(0, p0.x());
        assertEquals(100, p1.x());
        assertEquals(500, p2.x());
    }

    @Test
    void realA4ConfigDerivedFromActualCardPixelWidth() throws CardTooLargeException {
        int cardWidth = 992; // real rendered card width (496 nominal design px * 2x supersample)
        PageConfig a4 = PageConfig.a4FittingCardWidth(cardWidth);

        assertEquals(0, a4.portraitWidthPx() % cardWidth);
        assertEquals(5, a4.portraitWidthPx() / cardWidth);
        assertEquals(7, a4.landscapeWidthPx() / cardWidth);

        PrintPlan plan = SharePrintLayoutPlanner.plan(
                List.of(new CardInput(cardWidth, 1200), new CardInput(cardWidth, 1200)), a4);

        assertEquals(1, plan.pages().size());
        assertEquals(Orientation.LANDSCAPE, plan.pages().get(0).orientation());
    }
}
