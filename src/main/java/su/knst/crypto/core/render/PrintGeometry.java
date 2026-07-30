package su.knst.crypto.core.render;

/**
 * The single authority on how many device pixels make up a millimetre of printed output.
 *
 * Every printable artifact - card, tag, print sheet - is rasterized against this one density, so a
 * tag is the same physical size whether it is sent to the printer on its own or as part of a sheet.
 * Letting each call site pass its own DPI is what previously made the same tag come out ~6mm tall
 * standalone and ~25mm tall on a sheet.
 */
public final class PrintGeometry {
    /** A card is {@value CardImage#CARD_WIDTH} logical px wide, supersampled, and prints 56mm wide. */
    public static final double PX_PER_MM = (double) (CardImage.CARD_WIDTH * CardImage.RENDER_SCALE) / 56.0;

    public static final int PIXELS_PER_METER = (int) Math.round(PX_PER_MM * 1000);

    private PrintGeometry() {
    }

    public static int mmToPx(double mm) {
        return (int) Math.round(mm * PX_PER_MM);
    }

    public static double pxToMm(int px) {
        return px / PX_PER_MM;
    }
}
