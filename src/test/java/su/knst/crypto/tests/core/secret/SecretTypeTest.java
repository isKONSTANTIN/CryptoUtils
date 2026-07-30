package su.knst.crypto.tests.core.secret;

import org.junit.jupiter.api.Test;
import su.knst.crypto.core.secret.SecretType;

import static org.junit.jupiter.api.Assertions.*;

class SecretTypeTest {

    @Test
    void arbitraryDataIsCompressed() {
        assertTrue(SecretType.FILE.compressed());
        assertTrue(SecretType.TEXT.compressed());
    }

    @Test
    void alreadyDensePayloadsAreNot() {
        // 16 or 32 bytes of entropy: gzip adds a header and grows the QR payload for nothing
        assertFalse(SecretType.SEED.compressed());
        // an existing share's bytes have to reach the card untouched to still combine with siblings
        assertFalse(SecretType.HEX.compressed());
    }
}
