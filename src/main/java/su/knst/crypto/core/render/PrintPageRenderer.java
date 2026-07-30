package su.knst.crypto.core.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders {@link PrintLayoutPlanner.PrintPage}s into actual page-sized PNGs, blitting each
 * page's placed images at their planned positions. Images are always drawn upright (the planner
 * never rotates them - it picks sheet orientation instead), so this is a plain 1:1 blit with no
 * transforms.
 */
public final class PrintPageRenderer {
    private PrintPageRenderer() {
    }

    public static List<BufferedImage> render(List<PrintLayoutPlanner.PrintPage> pages) {
        List<BufferedImage> canvases = new ArrayList<>();

        for (PrintLayoutPlanner.PrintPage page : pages) {
            BufferedImage canvas = new BufferedImage(page.widthPx(), page.heightPx(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = canvas.createGraphics();

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, page.widthPx(), page.heightPx());

            for (PrintLayoutPlanner.PlacedImage item : page.items())
                g.drawImage(item.image(), item.x(), item.y(), null);

            g.dispose();

            canvases.add(canvas);
        }

        return canvases;
    }
}
