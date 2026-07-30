package su.knst.crypto.core.backup;

import su.knst.crypto.core.render.PngWriter;
import su.knst.crypto.core.render.PrintLayoutPlanner;
import su.knst.crypto.core.render.PrintPageRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tiles the rendered cards and tags onto printable sheets.
 *
 * The per-share PNGs are already written by the time this runs and are the authoritative backup;
 * sheets are a convenience. A layout failure therefore rolls back to no sheets at all - never a
 * partial set - and is reported rather than thrown.
 */
public final class SheetBuilder {
    /** @param failure why no sheets were produced, or empty when they were */
    public record Outcome(List<Path> files, Optional<String> failure) {
        public Outcome {
            files = List.copyOf(files);
        }

        static Outcome of(List<Path> files) {
            return new Outcome(files, Optional.empty());
        }

        static Outcome failed(String reason) {
            return new Outcome(List.of(), Optional.of(reason));
        }
    }

    private SheetBuilder() {
    }

    public static Outcome tryBuild(Path directory, String name, List<BufferedImage> artifacts) {
        // a lone artifact is already its own sheet; tiling it would just duplicate the file
        if (artifacts.size() < 2)
            return Outcome.of(List.of());

        List<Path> written = new ArrayList<>();

        try {
            int cardWidthPx = artifacts.get(0).getWidth();

            List<PrintLayoutPlanner.PrintPage> plan = PrintLayoutPlanner.plan(
                    artifacts, PrintLayoutPlanner.PageConfig.a4FittingCardWidth(cardWidthPx));

            List<BufferedImage> pages = PrintPageRenderer.render(plan);

            for (int i = 0; i < pages.size(); i++) {
                Path path = directory.resolve(name + "_print_" + (i + 1) + ".png");

                PngWriter.writeOwnerOnly(pages.get(i), path);
                written.add(path);
            }
        } catch (PrintLayoutPlanner.ImageTooLargeException | IOException e) {
            return Outcome.failed(e.getMessage());
        }

        return Outcome.of(written);
    }
}
