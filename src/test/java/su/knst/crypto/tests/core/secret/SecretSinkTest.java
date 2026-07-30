package su.knst.crypto.tests.core.secret;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import su.knst.crypto.core.secret.SecretException;
import su.knst.crypto.core.secret.SecretSink;
import su.knst.crypto.core.secret.SecretType;
import su.knst.crypto.core.seed.SeedService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecretSinkTest {

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
    void aFileSinkWritesOwnerOnly() throws Exception {
        byte[] content = "restored bytes".getBytes(StandardCharsets.UTF_8);

        Path file = Path.of("sink_test_file");
        filesToCleanUp.add(file);

        SecretSink sink = SecretSink.toFile(file);

        assertEquals(SecretType.FILE, sink.type());

        SecretSink.Written written = sink.write(content);

        assertEquals(file, written.file());
        assertNull(written.text());
        assertNull(written.words());
        assertArrayEquals(content, Files.readAllBytes(file));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
    }

    @Test
    void aFileSinkReportsAnUnwritablePath() {
        SecretSink sink = SecretSink.toFile(Path.of("sink_test_missing_dir/nested/output"));

        SecretException error = assertThrows(SecretException.class, () -> sink.write(new byte[] {1, 2, 3}));

        assertTrue(error.getMessage().contains("Failed to write output file"));
    }

    @Test
    void aTextSinkHandsBackTheDecodedString() throws Exception {
        String text = "восстановленный текст";

        SecretSink sink = SecretSink.toText();

        assertEquals(SecretType.TEXT, sink.type());

        SecretSink.Written written = sink.write(text.getBytes(StandardCharsets.UTF_8));

        assertEquals(text, written.text());
        assertNull(written.file());
        assertNull(written.words());
    }

    @Test
    void aSeedSinkHandsBackThePhrase() throws Exception {
        byte[] entropy = SeedService.randomEntropy(32);
        String[] expected = SeedService.fromEntropy(entropy);

        SecretSink sink = SecretSink.toSeed();

        assertEquals(SecretType.SEED, sink.type());

        SecretSink.Written written = sink.write(entropy);

        assertArrayEquals(expected, written.words());
        assertNull(written.file());
        assertNull(written.text());
    }

    @Test
    void aSeedSinkRejectsBytesThatAreNotEntropy() {
        // 7 bytes is not a valid BIP-39 entropy length
        assertThrows(SecretException.class, () -> SecretSink.toSeed().write(new byte[7]));
    }
}
