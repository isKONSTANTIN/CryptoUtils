package su.knst.crypto.tests.core.secret;

import org.junit.jupiter.api.Test;
import su.knst.crypto.core.secret.GzipCodec;
import su.knst.crypto.core.secret.SecretException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class GzipCodecTest {

    @Test
    void dataRoundTrips() throws Exception {
        byte[] data = "the quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(data, GzipCodec.decompress(GzipCodec.compress(data)));
    }

    @Test
    void repetitiveDataGetsSmaller() throws Exception {
        byte[] data = "A".repeat(4096).getBytes(StandardCharsets.UTF_8);

        assertTrue(GzipCodec.compress(data).length < data.length);
    }

    @Test
    void randomDataRoundTripsEvenThoughItDoesNotShrink() throws Exception {
        byte[] data = new byte[1024];
        new SecureRandom().nextBytes(data);

        assertArrayEquals(data, GzipCodec.decompress(GzipCodec.compress(data)));
    }

    @Test
    void emptyInputRoundTrips() throws Exception {
        assertArrayEquals(new byte[0], GzipCodec.decompress(GzipCodec.compress(new byte[0])));
    }

    @Test
    void somethingThatIsNotGzipIsRejected() {
        SecretException error = assertThrows(SecretException.class,
                () -> GzipCodec.decompress("not gzip at all".getBytes(StandardCharsets.UTF_8)));

        assertTrue(error.getMessage().contains("decompress"));
        assertNotNull(error.getCause());
    }

    @Test
    void truncatedGzipIsRejected() throws Exception {
        byte[] compressed = GzipCodec.compress("some payload worth truncating".getBytes(StandardCharsets.UTF_8));
        byte[] truncated = java.util.Arrays.copyOf(compressed, compressed.length / 2);

        assertThrows(SecretException.class, () -> GzipCodec.decompress(truncated));
    }
}
