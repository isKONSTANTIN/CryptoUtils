package su.knst.crypto.core.secret;

import su.knst.crypto.core.seed.SeedException;
import su.knst.crypto.core.seed.SeedService;
import su.knst.crypto.utils.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Where a reconstructed secret ends up. The variants mirror {@link SecretSource}, so what went into
 * a backup is what comes back out of it.
 */
public interface SecretSink {
    SecretType type();

    Written write(byte[] secret) throws SecretException;

    /**
     * @param file  path the secret was written to, or null when it was not written to disk
     * @param text  the secret as text, or null when it went to a file
     * @param words the recovered mnemonic, or null for the other types
     */
    record Written(Path file, String text, String[] words) {
    }

    static SecretSink toFile(Path path) {
        return new SecretSink() {
            @Override
            public SecretType type() {
                return SecretType.FILE;
            }

            @Override
            public Written write(byte[] secret) throws SecretException {
                try {
                    FileUtils.writeOwnerOnly(path, secret);
                } catch (IOException e) {
                    throw new SecretException("Failed to write output file: " + e.getMessage(), e);
                }

                return new Written(path, null, null);
            }
        };
    }

    static SecretSink toText() {
        return new SecretSink() {
            @Override
            public SecretType type() {
                return SecretType.TEXT;
            }

            @Override
            public Written write(byte[] secret) {
                return new Written(null, new String(secret, StandardCharsets.UTF_8), null);
            }
        };
    }

    static SecretSink toSeed() {
        return new SecretSink() {
            @Override
            public SecretType type() {
                return SecretType.SEED;
            }

            @Override
            public Written write(byte[] secret) throws SecretException {
                try {
                    return new Written(null, null, SeedService.fromEntropy(secret));
                } catch (SeedException e) {
                    throw new SecretException(e.getMessage(), e);
                }
            }
        };
    }
}
