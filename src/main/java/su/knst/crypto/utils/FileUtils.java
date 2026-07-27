package su.knst.crypto.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class FileUtils {
    // Creates an empty file with owner-only (0600) permissions already in place, before any bytes
    // are ever written to it, so the data is never briefly readable under the process' default umask.
    // Any following write to the returned path (opened without recreating the file) keeps these permissions.
    public static void createOwnerOnly(Path path) throws IOException {
        Files.deleteIfExists(path);

        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);

            Files.createFile(path, attr);
        } else {
            Files.createFile(path);

            path.toFile().setReadable(false, false);
            path.toFile().setWritable(false, false);
            path.toFile().setReadable(true, true);
            path.toFile().setWritable(true, true);
        }
    }

    public static void writeOwnerOnly(Path path, byte[] data) throws IOException {
        createOwnerOnly(path);
        Files.write(path, data);
    }

    public static void writeOwnerOnly(Path path, String data) throws IOException {
        writeOwnerOnly(path, data.getBytes(StandardCharsets.UTF_8));
    }
}
