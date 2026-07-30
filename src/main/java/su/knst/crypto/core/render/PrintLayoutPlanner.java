package su.knst.crypto.core.render;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides how to tile a list of arbitrary-sized images (backup share cards, container tags, or
 * anything else) onto as few A4 sheets as possible for printing. Reads each image's own pixel
 * dimensions - callers don't need to extract them - and returns full pages, each carrying its own
 * placed images, ready to be rendered by {@link PrintPageRenderer}.
 *
 * Items are always drawn upright (never rotated); instead the SHEET orientation is chosen per
 * item. A landscape A4 sheet (297mm wide, 210mm tall) is preferred by default. An item that
 * doesn't fit a landscape sheet is routed to a portrait sheet instead, whose height (297mm - the
 * paper's actual long edge) is the most vertical room any single A4 sheet can ever offer, in
 * either orientation. An item that doesn't fit EITHER orientation genuinely cannot be printed on
 * a single A4 sheet: {@link #plan} throws {@link ImageTooLargeException} and produces no plan at
 * all rather than a partial one, since a print run either fully succeeds or doesn't happen.
 *
 * Packing within a sheet uses Shelf Best-Fit Decreasing Height (BFDH): images routed to the same
 * orientation are sorted tallest-first, then each is placed into whichever already-open shelf on
 * the current page wastes the least leftover width (best fit), rather than always appending to the
 * newest shelf. Because images arrive tallest-first, a shelf's height is fixed by the first image
 * placed into it and never grows afterward - this is what keeps BFDH efficient under wildly
 * different image sizes. Only when no open shelf fits does a new shelf open below the last one, or
 * a new page if the page is out of vertical room too.
 */
public final class PrintLayoutPlanner {
    private PrintLayoutPlanner() {
    }

    public enum Orientation {
        PORTRAIT, LANDSCAPE
    }

    public record PlacedImage(BufferedImage image, int x, int y) {
    }

    public record PrintPage(Orientation orientation, int widthPx, int heightPx, List<PlacedImage> items) {
    }

    public record PageConfig(int portraitWidthPx, int portraitHeightPx) {
        // Anchoring the page to a card's own already-rendered pixel width keeps card pixels and
        // page pixels in the same scale whatever supersampling the renderers use, so the layout
        // never has to know a DPI. A card is 56mm wide (see PrintGeometry, which writes that same
        // density into every PNG), which makes these dimensions a true A4 sheet.
        public static PageConfig a4FittingCardWidth(int cardWidthPx) {
            double pxPerMm = cardWidthPx / 56.0;

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

    public static class ImageTooLargeException extends Exception {
        public ImageTooLargeException(String message) {
            super(message);
        }
    }

    public static List<PrintPage> plan(List<BufferedImage> images, PageConfig pageConfig)
            throws ImageTooLargeException {
        validateEveryImageFitsSomewhere(images, pageConfig);

        List<BufferedImage> landscapeImages = new ArrayList<>();
        List<BufferedImage> portraitImages = new ArrayList<>();

        for (BufferedImage image : images) {
            if (fitsLandscape(image, pageConfig))
                landscapeImages.add(image);
            else
                portraitImages.add(image);
        }

        List<PrintPage> pages = new ArrayList<>();

        packOrientation(landscapeImages, Orientation.LANDSCAPE,
                pageConfig.landscapeWidthPx(), pageConfig.landscapeHeightPx(), pages);
        packOrientation(portraitImages, Orientation.PORTRAIT,
                pageConfig.portraitWidthPx(), pageConfig.portraitHeightPx(), pages);

        return pages;
    }

    private static boolean fitsLandscape(BufferedImage image, PageConfig pageConfig) {
        return image.getWidth() <= pageConfig.landscapeWidthPx()
                && image.getHeight() <= pageConfig.landscapeHeightPx();
    }

    private static void validateEveryImageFitsSomewhere(List<BufferedImage> images, PageConfig pageConfig)
            throws ImageTooLargeException {
        for (int i = 0; i < images.size(); i++) {
            BufferedImage image = images.get(i);

            boolean fitsLandscape = fitsLandscape(image, pageConfig);
            boolean fitsPortrait = image.getWidth() <= pageConfig.portraitWidthPx()
                    && image.getHeight() <= pageConfig.portraitHeightPx();

            if (!fitsLandscape && !fitsPortrait)
                throw new ImageTooLargeException("image at position " + i + " (" + image.getWidth() + "x"
                        + image.getHeight() + "px) does not fit on an A4 sheet in either orientation");
        }
    }

    // Mutable packing state for one shelf (row) on the page currently being filled.
    private static final class Shelf {
        final int y;
        final int height;
        int remainingWidth;

        Shelf(int y, int height, int remainingWidth) {
            this.y = y;
            this.height = height;
            this.remainingWidth = remainingWidth;
        }
    }

    private static void packOrientation(List<BufferedImage> images, Orientation orientation,
                                         int pageWidthPx, int pageHeightPx, List<PrintPage> pages) {
        if (images.isEmpty())
            return;

        List<BufferedImage> sorted = new ArrayList<>(images);
        sorted.sort(Comparator.comparingInt((BufferedImage image) -> image.getHeight()).reversed());

        List<PlacedImage> currentPageItems = null;
        List<Shelf> openShelves = null;
        int usedPageHeight = 0;

        for (BufferedImage image : sorted) {
            int w = image.getWidth();
            int h = image.getHeight();

            Shelf bestShelf = null;

            if (openShelves != null) {
                int bestWaste = Integer.MAX_VALUE;

                for (Shelf shelf : openShelves) {
                    if (shelf.remainingWidth >= w) {
                        int waste = shelf.remainingWidth - w;

                        if (waste < bestWaste) {
                            bestWaste = waste;
                            bestShelf = shelf;
                        }
                    }
                }
            }

            if (bestShelf != null) {
                int x = pageWidthPx - bestShelf.remainingWidth;

                currentPageItems.add(new PlacedImage(image, x, bestShelf.y));
                bestShelf.remainingWidth -= w;
                continue;
            }

            if (openShelves != null && usedPageHeight + h <= pageHeightPx) {
                Shelf shelf = new Shelf(usedPageHeight, h, pageWidthPx - w);
                openShelves.add(shelf);
                currentPageItems.add(new PlacedImage(image, 0, shelf.y));
                usedPageHeight += h;
                continue;
            }

            if (currentPageItems != null)
                pages.add(new PrintPage(orientation, pageWidthPx, pageHeightPx, currentPageItems));

            currentPageItems = new ArrayList<>();
            openShelves = new ArrayList<>();

            Shelf shelf = new Shelf(0, h, pageWidthPx - w);
            openShelves.add(shelf);
            currentPageItems.add(new PlacedImage(image, 0, shelf.y));
            usedPageHeight = h;
        }

        if (currentPageItems != null)
            pages.add(new PrintPage(orientation, pageWidthPx, pageHeightPx, currentPageItems));
    }
}
