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
    /**
     * A card prints exactly one fifth of a landscape A4's 297mm width, so five of them sit across a
     * sheet edge to edge. {@link CardImage} derives its pixel width from this, not the other way
     * round: the density below has to stay fixed, or every other artifact sized in raw pixels (the
     * tag's 25.4mm height above all) would silently change physical size along with the card.
     */
    public static final double CARD_WIDTH_MM = 297.0 / 5.0;

    /** 450 DPI - the density every printable artifact is rasterized and tagged with. */
    public static final double PX_PER_MM = 450.0 / 25.4;

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
