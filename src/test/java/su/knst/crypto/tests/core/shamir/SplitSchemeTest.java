package su.knst.crypto.tests.core.shamir;

import org.junit.jupiter.api.Test;
import su.knst.crypto.core.shamir.SplitScheme;

import static org.junit.jupiter.api.Assertions.*;

class SplitSchemeTest {

    @Test
    void validSchemeIsAccepted() {
        SplitScheme scheme = SplitScheme.of(5, 3);

        assertEquals(5, scheme.total());
        assertEquals(3, scheme.threshold());
        assertTrue(scheme.isSplit());
    }

    @Test
    void thresholdBelowTwoIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SplitScheme.of(5, 1));
        assertThrows(IllegalArgumentException.class, () -> SplitScheme.of(5, 0));
    }

    @Test
    void totalBelowThresholdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SplitScheme.of(2, 3));
    }

    @Test
    void totalAboveMaxIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SplitScheme.of(256, 2));
        assertDoesNotThrow(() -> SplitScheme.of(255, 2));
    }

    @Test
    void singleIsOneOfOneAndNotSplit() {
        SplitScheme scheme = SplitScheme.single();

        assertEquals(1, scheme.total());
        assertEquals(1, scheme.threshold());
        assertFalse(scheme.isSplit());
    }
}
