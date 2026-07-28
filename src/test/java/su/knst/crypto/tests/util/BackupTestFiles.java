package su.knst.crypto.tests.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Finds the print-sheet PNGs a BackupCreateCommand run produced for a given backup name, so
 * tests can register them for cleanup - their count varies with card count/height and isn't
 * known up front the way the per-share "_1.png".."_N.png" files are.
 */
public final class BackupTestFiles {
    private BackupTestFiles() {
    }

    public static List<Path> printSheetPaths(String name) {
        try (var stream = Files.list(Path.of("."))) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(name + "_print_"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
