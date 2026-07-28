package su.knst.crypto.utils.codes;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link SharePrintLayoutPlanner.PrintPlan} into actual page-sized PNGs, blitting each
 * image at its planned position. {@code images} must be positionally parallel to the
 * {@code cards} list originally passed to {@link SharePrintLayoutPlanner#plan} - {@code images.get(i)}
 * is drawn using {@code plan.placements().get(i)}. Images are always drawn upright (the planner
 * never rotates them - it picks sheet orientation instead), so this is a plain 1:1 blit with no
 * transforms.
 */
public final class SharePrintPageRenderer {
    private SharePrintPageRenderer() {
    }

    public static List<BufferedImage> render(SharePrintLayoutPlanner.PrintPlan plan, List<BufferedImage> images) {
        List<BufferedImage> canvases = new ArrayList<>();

        for (SharePrintLayoutPlanner.PrintPage page : plan.pages()) {
            BufferedImage canvas = new BufferedImage(page.widthPx(), page.heightPx(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = canvas.createGraphics();

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, page.widthPx(), page.heightPx());
            g.dispose();

            canvases.add(canvas);
        }

        List<SharePrintLayoutPlanner.Placement> placements = plan.placements();

        for (int i = 0; i < images.size(); i++) {
            SharePrintLayoutPlanner.Placement placement = placements.get(i);
            BufferedImage canvas = canvases.get(placement.pageIndex());

            Graphics2D g = canvas.createGraphics();
            g.drawImage(images.get(i), placement.x(), placement.y(), null);
            g.dispose();
        }

        return canvases;
    }
}
