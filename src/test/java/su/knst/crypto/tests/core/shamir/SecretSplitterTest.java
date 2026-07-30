package su.knst.crypto.tests.core.shamir;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import su.knst.crypto.core.shamir.SecretJoiner;
import su.knst.crypto.core.shamir.SecretSplitter;
import su.knst.crypto.core.shamir.Share;
import su.knst.crypto.core.shamir.ShareSet;
import su.knst.crypto.core.shamir.SplitScheme;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecretSplitterTest {

    static final SecureRandom RANDOM = new SecureRandom();

    @ParameterizedTest
    @CsvSource({"2,2", "3,2", "5,3", "7,4", "10,10"})
    void anyThresholdSubsetReconstructsTheSecret(int total, int threshold) {
        byte[] secret = new byte[64];
        RANDOM.nextBytes(secret);

        ShareSet shares = SecretSplitter.shamir(SplitScheme.of(total, threshold)).split(secret);

        assertEquals(total, shares.size());

        for (int attempt = 0; attempt < 5; attempt++) {
            List<Share> subset = new ArrayList<>(shares.shares());
            Collections.shuffle(subset, RANDOM);

            assertArrayEquals(secret, SecretJoiner.join(subset.subList(0, threshold)));
        }
    }

    @Test
    void sharesAreNumberedFromOne() {
        ShareSet shares = SecretSplitter.shamir(SplitScheme.of(4, 2)).split("payload".getBytes(StandardCharsets.UTF_8));

        for (int i = 1; i <= 4; i++)
            assertEquals(i, shares.get(i).index());
    }

    @Test
    void onlyAShamirSplitIsRandomized() {
        // a fresh polynomial every call is what makes re-splitting worth trying after a card
        // failed to decode back; the other two have fixed output, so a retry would be pointless
        assertTrue(SecretSplitter.shamir(SplitScheme.of(3, 2)).randomized());
        assertFalse(SecretSplitter.single().randomized());
        assertFalse(SecretSplitter.reprint(1, SplitScheme.of(3, 2)).randomized());
    }

    @Test
    void everySplitterReportsTheSchemeItWorksTo() {
        assertEquals(SplitScheme.of(5, 3), SecretSplitter.shamir(SplitScheme.of(5, 3)).scheme());
        assertEquals(SplitScheme.single(), SecretSplitter.single().scheme());
        assertEquals(SplitScheme.of(4, 2), SecretSplitter.reprint(2, SplitScheme.of(4, 2)).scheme());
    }

    @Test
    void shamirRejectsANonSplitScheme() {
        assertThrows(IllegalArgumentException.class, () -> SecretSplitter.shamir(SplitScheme.single()));
    }

    @Test
    void singleShareHoldsTheWholeSecretUntouched() {
        byte[] secret = "the whole thing".getBytes(StandardCharsets.UTF_8);
        SecretSplitter splitter = SecretSplitter.single();

        ShareSet shares = splitter.split(secret);

        assertEquals(1, shares.size());
        assertEquals(1, shares.get(1).index());
        assertArrayEquals(secret, shares.get(1).data());
    }

    @Test
    void reprintKeepsTheIndexAndPayload() {
        byte[] payload = new byte[32];
        RANDOM.nextBytes(payload);

        SecretSplitter splitter = SecretSplitter.reprint(3, SplitScheme.of(5, 2));
        ShareSet shares = splitter.split(payload);

        assertEquals(1, shares.size());
        assertEquals(3, shares.get(3).index());
        assertArrayEquals(payload, shares.get(3).data());
        assertEquals(5, shares.scheme().total());
    }

    @Test
    void reprintRejectsAnIndexOutsideTheScheme() {
        SplitScheme scheme = SplitScheme.of(3, 2);

        assertThrows(IllegalArgumentException.class, () -> SecretSplitter.reprint(0, scheme));
        assertThrows(IllegalArgumentException.class, () -> SecretSplitter.reprint(4, scheme));
    }

    @Test
    void joiningNothingIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SecretJoiner.join(List.of()));
    }
}
