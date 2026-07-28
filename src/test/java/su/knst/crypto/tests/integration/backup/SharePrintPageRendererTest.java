package su.knst.crypto.tests.integration.backup;

import org.junit.jupiter.api.Test;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.Orientation;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.Placement;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PrintPage;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PrintPlan;
import su.knst.crypto.utils.codes.SharePrintPageRenderer;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SharePrintPageRendererTest {

    private static BufferedImage solidCard(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    @Test
    void outputPageCountAndDimensionsMatchThePlan() {
        PrintPage page1 = new PrintPage(Orientation.LANDSCAPE, 200, 300);
        PrintPage page2 = new PrintPage(Orientation.PORTRAIT, 200, 300);

        PrintPlan plan = new PrintPlan(
                List.of(page1, page2),
                List.of(new Placement(0, 0, 0), new Placement(1, 0, 0)));

        List<BufferedImage> images = List.of(
                solidCard(50, 50, Color.RED),
                solidCard(50, 50, Color.BLUE));

        List<BufferedImage> rendered = SharePrintPageRenderer.render(plan, images);

        assertEquals(2, rendered.size());
        assertEquals(200, rendered.get(0).getWidth());
        assertEquals(300, rendered.get(0).getHeight());
    }

    @Test
    void cardIsBlittedAtItsExactPositionUpright() {
        BufferedImage card = solidCard(20, 10, Color.RED);

        PrintPage page = new PrintPage(Orientation.LANDSCAPE, 100, 100);
        PrintPlan plan = new PrintPlan(List.of(page), List.of(new Placement(0, 30, 40)));

        BufferedImage result = SharePrintPageRenderer.render(plan, List.of(card)).get(0);

        assertEquals(Color.RED.getRGB(), result.getRGB(30, 40));
        assertEquals(Color.RED.getRGB(), result.getRGB(49, 49));
        assertEquals(Color.WHITE.getRGB(), result.getRGB(0, 0));
        assertEquals(Color.WHITE.getRGB(), result.getRGB(30, 55));
    }

    @Test
    void placementsMapPositionallyToTheImagesList() {
        BufferedImage red = solidCard(10, 10, Color.RED);
        BufferedImage blue = solidCard(10, 10, Color.BLUE);

        PrintPage page = new PrintPage(Orientation.LANDSCAPE, 100, 100);
        PrintPlan plan = new PrintPlan(List.of(page),
                List.of(new Placement(0, 0, 0), new Placement(0, 20, 0)));

        BufferedImage result = SharePrintPageRenderer.render(plan, List.of(red, blue)).get(0);

        assertEquals(Color.RED.getRGB(), result.getRGB(5, 5));
        assertEquals(Color.BLUE.getRGB(), result.getRGB(25, 5));
    }
}
