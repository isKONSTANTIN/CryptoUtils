package su.knst.crypto.tests.integration.backup;

import org.junit.jupiter.api.Test;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.Orientation;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PlacedImage;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner.PrintPage;
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
        BufferedImage red = solidCard(50, 50, Color.RED);
        BufferedImage blue = solidCard(50, 50, Color.BLUE);

        PrintPage page1 = new PrintPage(Orientation.LANDSCAPE, 200, 300, List.of(new PlacedImage(red, 0, 0)));
        PrintPage page2 = new PrintPage(Orientation.PORTRAIT, 200, 300, List.of(new PlacedImage(blue, 0, 0)));

        List<BufferedImage> rendered = SharePrintPageRenderer.render(List.of(page1, page2));

        assertEquals(2, rendered.size());
        assertEquals(200, rendered.get(0).getWidth());
        assertEquals(300, rendered.get(0).getHeight());
    }

    @Test
    void cardIsBlittedAtItsExactPositionUpright() {
        BufferedImage card = solidCard(20, 10, Color.RED);

        PrintPage page = new PrintPage(Orientation.LANDSCAPE, 100, 100, List.of(new PlacedImage(card, 30, 40)));

        BufferedImage result = SharePrintPageRenderer.render(List.of(page)).get(0);

        assertEquals(Color.RED.getRGB(), result.getRGB(30, 40));
        assertEquals(Color.RED.getRGB(), result.getRGB(49, 49));
        assertEquals(Color.WHITE.getRGB(), result.getRGB(0, 0));
        assertEquals(Color.WHITE.getRGB(), result.getRGB(30, 55));
    }

    @Test
    void multipleItemsOnTheSamePageAreAllBlitted() {
        BufferedImage red = solidCard(10, 10, Color.RED);
        BufferedImage blue = solidCard(10, 10, Color.BLUE);

        PrintPage page = new PrintPage(Orientation.LANDSCAPE, 100, 100, List.of(
                new PlacedImage(red, 0, 0),
                new PlacedImage(blue, 20, 0)));

        BufferedImage result = SharePrintPageRenderer.render(List.of(page)).get(0);

        assertEquals(Color.RED.getRGB(), result.getRGB(5, 5));
        assertEquals(Color.BLUE.getRGB(), result.getRGB(25, 5));
    }
}
