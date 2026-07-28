package su.knst.crypto.utils.codes;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides how to tile a list of rectangular print items (backup share cards, but also arbitrary
 * other images/labels of their own size) onto as few A4 sheets as possible for printing. Pure
 * data in/data out - no AWT dependency - so the packing logic can be unit tested without
 * rendering anything.
 *
 * Items are given as a plain list and never reordered or tagged with an index: the returned
 * {@link PrintPlan#placements()} list is positional - entry {@code i} is where {@code cards.get(i)}
 * landed - so the caller zips its own image list against both lists by position.
 *
 * Items are always drawn upright (never rotated); instead the SHEET orientation is chosen per
 * item. A landscape A4 sheet (297mm wide, 210mm tall) is preferred by default. An item that
 * doesn't fit a landscape sheet is routed to a portrait sheet instead, whose height (297mm - the
 * paper's actual long edge) is the most vertical room any single A4 sheet can ever offer, in
 * either orientation. An item that doesn't fit EITHER orientation genuinely cannot be printed on
 * a single A4 sheet: {@link #plan} throws {@link CardTooLargeException} and produces no plan at
 * all rather than a partial one, since a print run either fully succeeds or doesn't happen.
 *
 * Packing within a sheet is shelf-based: items are placed left to right into the current shelf
 * (row) as long as they fit its remaining width and the shelf's height (which grows to fit the
 * tallest item placed in it so far, as long as growing it still fits the page); once an item
 * doesn't fit, the current shelf is closed and a new one opens below it, or a new page if the
 * page is out of vertical room too.
 */
public final class SharePrintLayoutPlanner {
    private SharePrintLayoutPlanner() {
    }

    public enum Orientation {
        PORTRAIT, LANDSCAPE
    }

    public record CardInput(int widthPx, int heightPx) {
    }

    public record Placement(int pageIndex, int x, int y) {
    }

    public record PrintPage(Orientation orientation, int widthPx, int heightPx) {
    }

    public record PrintPlan(List<PrintPage> pages, List<Placement> placements) {
    }

    public record PageConfig(int portraitWidthPx, int portraitHeightPx) {
        // A share card is always exactly 42mm wide (1/5 of A4's 210mm width) by design - see
        // ShareCardImage - regardless of how many raw pixels that renders to (supersampling
        // factors etc.), so deriving the page size from the actual rendered card width keeps
        // this correct without depending on ShareCardImage's internal pixel/DPI conventions.
        public static PageConfig a4FittingCardWidth(int cardWidthPx) {
            double pxPerMm = cardWidthPx / 42.0;

            return new PageConfig(
                    (int) Math.round(210 * pxPerMm),
                    (int) Math.round(297 * pxPerMm));
        }

        public int landscapeWidthPx() {
            return portraitHeightPx;
        }

        public int landscapeHeightPx() {
            return portraitWidthPx;
        }
    }

    public static class CardTooLargeException extends Exception {
        public CardTooLargeException(String message) {
            super(message);
        }
    }

    // Mutable packing state for the page currently being filled in one orientation: which page
    // (by index into the shared, global page list) and the state of its current, still-open shelf.
    private static final class PageCursor {
        final int pageIndex;
        int shelfY;
        int shelfHeight;
        int shelfRemainingWidth;

        PageCursor(int pageIndex, int shelfY, int shelfHeight, int shelfRemainingWidth) {
            this.pageIndex = pageIndex;
            this.shelfY = shelfY;
            this.shelfHeight = shelfHeight;
            this.shelfRemainingWidth = shelfRemainingWidth;
        }
    }

    public static PrintPlan plan(List<CardInput> cards, PageConfig pageConfig) throws CardTooLargeException {
        validateEveryCardFitsSomewhere(cards, pageConfig);

        List<PrintPage> pages = new ArrayList<>();
        List<Placement> placements = new ArrayList<>();

        PageCursor landscapeCursor = null;
        PageCursor portraitCursor = null;

        for (CardInput card : cards) {
            boolean fitsLandscape = card.widthPx() <= pageConfig.landscapeWidthPx()
                    && card.heightPx() <= pageConfig.landscapeHeightPx();

            if (fitsLandscape) {
                PlaceResult result = place(card, landscapeCursor, Orientation.LANDSCAPE,
                        pageConfig.landscapeWidthPx(), pageConfig.landscapeHeightPx(), pages);
                landscapeCursor = result.cursor();
                placements.add(result.placement());
            } else {
                PlaceResult result = place(card, portraitCursor, Orientation.PORTRAIT,
                        pageConfig.portraitWidthPx(), pageConfig.portraitHeightPx(), pages);
                portraitCursor = result.cursor();
                placements.add(result.placement());
            }
        }

        return new PrintPlan(pages, placements);
    }

    private static void validateEveryCardFitsSomewhere(List<CardInput> cards, PageConfig pageConfig)
            throws CardTooLargeException {
        for (int i = 0; i < cards.size(); i++) {
            CardInput card = cards.get(i);

            boolean fitsLandscape = card.widthPx() <= pageConfig.landscapeWidthPx()
                    && card.heightPx() <= pageConfig.landscapeHeightPx();
            boolean fitsPortrait = card.widthPx() <= pageConfig.portraitWidthPx()
                    && card.heightPx() <= pageConfig.portraitHeightPx();

            if (!fitsLandscape && !fitsPortrait)
                throw new CardTooLargeException("card at position " + i + " (" + card.widthPx() + "x"
                        + card.heightPx() + "px) does not fit on an A4 sheet in either orientation");
        }
    }

    private record PlaceResult(PageCursor cursor, Placement placement) {
    }

    private static PlaceResult place(CardInput card, PageCursor cursor, Orientation orientation,
                                      int pageWidthPx, int pageHeightPx, List<PrintPage> pages) {
        int w = card.widthPx();
        int h = card.heightPx();

        if (cursor != null) {
            if (cursor.shelfRemainingWidth >= w) {
                int grownShelfHeight = Math.max(cursor.shelfHeight, h);

                if (cursor.shelfY + grownShelfHeight <= pageHeightPx) {
                    int x = pageWidthPx - cursor.shelfRemainingWidth;
                    int y = cursor.shelfY;

                    cursor.shelfRemainingWidth -= w;
                    cursor.shelfHeight = grownShelfHeight;

                    return new PlaceResult(cursor, new Placement(cursor.pageIndex, x, y));
                }
            }

            int newShelfY = cursor.shelfY + cursor.shelfHeight;

            if (newShelfY + h <= pageHeightPx) {
                cursor.shelfY = newShelfY;
                cursor.shelfHeight = h;
                cursor.shelfRemainingWidth = pageWidthPx - w;

                return new PlaceResult(cursor, new Placement(cursor.pageIndex, 0, newShelfY));
            }
        }

        PrintPage freshPage = new PrintPage(orientation, pageWidthPx, pageHeightPx);
        pages.add(freshPage);

        PageCursor freshCursor = new PageCursor(pages.size() - 1, 0, h, pageWidthPx - w);

        return new PlaceResult(freshCursor, new Placement(freshCursor.pageIndex, 0, 0));
    }
}
