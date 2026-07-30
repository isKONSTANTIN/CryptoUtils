package su.knst.crypto.core.render;

import su.knst.crypto.utils.FileUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes a rendered artifact to a PNG that prints at its intended physical size.
 *
 * There is deliberately no DPI or render-scale parameter: the density comes from
 * {@link PrintGeometry}, so a caller cannot accidentally give the same artifact a different
 * physical size than it has everywhere else.
 */
public final class PngWriter {
    private PngWriter() {
    }

    /** Creates the file owner-only first, then writes the image into it. */
    public static void writeOwnerOnly(BufferedImage image, Path path) throws IOException {
        FileUtils.createOwnerOnly(path);
        write(image, path);
    }

    public static void write(BufferedImage image, Path path) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();

        ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(image.getType());
        IIOMetadata metadata = writer.getDefaultImageMetadata(typeSpecifier, writeParam);

        String formatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = new IIOMetadataNode(formatName);

        IIOMetadataNode pHYs = new IIOMetadataNode("pHYs");
        pHYs.setAttribute("pixelsPerUnitXAxis", Integer.toString(PrintGeometry.PIXELS_PER_METER));
        pHYs.setAttribute("pixelsPerUnitYAxis", Integer.toString(PrintGeometry.PIXELS_PER_METER));
        pHYs.setAttribute("unitSpecifier", "meter");
        root.appendChild(pHYs);

        metadata.mergeTree(formatName, root);

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, metadata), writeParam);
        } finally {
            writer.dispose();
        }
    }
}
