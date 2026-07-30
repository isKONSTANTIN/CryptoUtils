package su.knst.crypto.tests.core.secret;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import su.knst.crypto.core.secret.SecretException;
import su.knst.crypto.core.secret.SecretSource;
import su.knst.crypto.core.secret.SecretType;
import su.knst.crypto.core.seed.SeedService;
import su.knst.crypto.utils.HexUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecretSourceTest {

    List<Path> filesToCleanUp;

    @BeforeEach
    void setUp() {
        filesToCleanUp = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Path path : filesToCleanUp)
            Files.deleteIfExists(path);
    }

    @Test
    void aFileSourceReadsItsBytes() throws Exception {
        byte[] content = "file contents".getBytes(StandardCharsets.UTF_8);

        Path file = Path.of("source_test_file");
        filesToCleanUp.add(file);
        Files.write(file, content);

        SecretSource source = SecretSource.ofFile(file);

        assertEquals(SecretType.FILE, source.type());
        assertArrayEquals(content, source.read());
    }

    @Test
    void aMissingFileIsReported() {
        SecretSource source = SecretSource.ofFile(Path.of("source_test_definitely_missing"));

        SecretException error = assertThrows(SecretException.class, source::read);

        assertTrue(error.getMessage().contains("Failed to read source file"));
        assertNotNull(error.getCause());
    }

    @Test
    void aTextSourceReadsItsUtf8Bytes() throws Exception {
        String text = "текст с юникодом";

        SecretSource source = SecretSource.ofText(text);

        assertEquals(SecretType.TEXT, source.type());
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), source.read());
    }

    @Test
    void aSeedSourceReadsTheEntropyBehindThePhrase() throws Exception {
        byte[] entropy = SeedService.randomEntropy(32);
        String[] mnemonic = SeedService.fromEntropy(entropy);

        SecretSource source = SecretSource.ofSeed(mnemonic);

        assertEquals(SecretType.SEED, source.type());
        assertArrayEquals(entropy, source.read());
    }

    @Test
    void anInvalidPhraseIsReported() {
        String[] words = new String[12];
        Arrays.fill(words, "abandon");
        words[5] = "definitelynotabip39word";

        assertThrows(SecretException.class, SecretSource.ofSeed(words)::read);
    }

    @Test
    void aHexSourceReadsTheBytesItNames() throws Exception {
        SecretSource source = SecretSource.ofHex("00FF10AB");

        assertEquals(SecretType.HEX, source.type());
        assertArrayEquals(HexUtils.hexStringToByteArray("00FF10AB"), source.read());
    }

    @Test
    void anInvalidHexSourceIsReported() {
        SecretException error = assertThrows(SecretException.class, SecretSource.ofHex("nothex")::read);

        assertTrue(error.getMessage().contains("hex"));
    }

    @Test
    void oddLengthHexIsRejected() {
        assertThrows(SecretException.class, SecretSource.ofHex("ABC")::read);
    }
}
