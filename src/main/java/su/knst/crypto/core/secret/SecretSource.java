package su.knst.crypto.core.secret;

import su.knst.crypto.core.seed.SeedException;
import su.knst.crypto.core.seed.SeedService;
import su.knst.crypto.utils.HexUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the bytes to back up come from. Reading and validating happens here rather than in the
 * command, so a command only has to collect the answer and hand over a source.
 */
public interface SecretSource {
    SecretType type();

    byte[] read() throws SecretException;

    static SecretSource ofFile(Path path) {
        return new SecretSource() {
            @Override
            public SecretType type() {
                return SecretType.FILE;
            }

            @Override
            public byte[] read() throws SecretException {
                try {
                    return Files.readAllBytes(path);
                } catch (IOException e) {
                    throw new SecretException("Failed to read source file: " + e.getMessage(), e);
                }
            }
        };
    }

    static SecretSource ofText(String text) {
        return new SecretSource() {
            @Override
            public SecretType type() {
                return SecretType.TEXT;
            }

            @Override
            public byte[] read() {
                return text.getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    static SecretSource ofSeed(String[] words) {
        return new SecretSource() {
            @Override
            public SecretType type() {
                return SecretType.SEED;
            }

            @Override
            public byte[] read() throws SecretException {
                try {
                    return SeedService.toEntropy(words);
                } catch (SeedException e) {
                    throw new SecretException(e.getMessage(), e);
                }
            }
        };
    }

    /** Raw share bytes, typed in as hex when reprinting a card for a share that already exists. */
    static SecretSource ofHex(String hex) {
        return new SecretSource() {
            @Override
            public SecretType type() {
                return SecretType.HEX;
            }

            @Override
            public byte[] read() throws SecretException {
                if (!HexUtils.isValidHex(hex))
                    throw new SecretException("Invalid hex string");

                return HexUtils.hexStringToByteArray(hex);
            }
        };
    }
}
