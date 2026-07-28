package su.knst.crypto.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * In a GraalVM native-image build, java.desktop's AWT classes (BufferedImage,
 * Graphics2D, ImageIO) load a handful of shared libraries via
 * System.loadLibrary at first use, some of which only exist as a
 * native-image build artifact (see build.gradle's AWT bootstrap tasks).
 * native-image can't statically link them into the executable, so they're
 * embedded as resources at build time and extracted to a temp directory
 * here, before anything touches java.desktop, so the distributed artifact
 * is still a single file. manifest.txt (written by the same build step)
 * lists exactly which files were embedded, since that can vary by
 * platform/JDK version.
 */
public final class NativeAwtLibraries {
    private static final String RESOURCE_DIR = "/su/knst/crypto/native-libs/";

    private NativeAwtLibraries() {
    }

    public static void extractAndRegister() {
        if (System.getProperty("org.graalvm.nativeimage.imagecode") == null)
            return;

        try {
            List<String> names = readManifest();

            if (names.isEmpty())
                return;

            Path dir = Files.createTempDirectory("cryptoutils-awt-");
            dir.toFile().deleteOnExit();

            for (String name : names) {
                Path target = dir.resolve(name);
                target.toFile().deleteOnExit();

                try (InputStream in = NativeAwtLibraries.class.getResourceAsStream(RESOURCE_DIR + name)) {
                    if (in == null)
                        continue;

                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            System.setProperty("java.library.path", dir.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> readManifest() throws IOException {
        try (InputStream in = NativeAwtLibraries.class.getResourceAsStream(RESOURCE_DIR + "manifest.txt")) {
            if (in == null)
                return List.of();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().filter(line -> !line.isBlank()).toList();
            }
        }
    }
}
