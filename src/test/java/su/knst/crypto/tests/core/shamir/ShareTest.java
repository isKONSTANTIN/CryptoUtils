package su.knst.crypto.tests.core.shamir;

import org.junit.jupiter.api.Test;
import su.knst.crypto.core.shamir.Share;
import su.knst.crypto.core.shamir.ShareSet;
import su.knst.crypto.core.shamir.SplitScheme;
import su.knst.crypto.utils.HexUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShareTest {

    @Test
    void hexIsTheBareUppercasePayload() {
        Share share = new Share(1, new byte[] {0x00, (byte) 0xAB, 0x10, (byte) 0xFF});

        assertEquals("00AB10FF", share.hex());
        assertArrayEquals(share.data(), HexUtils.hexStringToByteArray(share.hex()));
    }

    @Test
    void theChecksumIsSixLowercaseHexCharacters() {
        String checksum = new Share(1, "payload".getBytes()).checksum();

        assertEquals(6, checksum.length());
        assertEquals(checksum.toLowerCase(), checksum);
        assertTrue(HexUtils.isValidHex(checksum), checksum + " should be hex");
    }

    @Test
    void theChecksumFollowsTheBytesNotTheIndex() {
        byte[] data = "same bytes".getBytes();

        assertEquals(new Share(1, data).checksum(), new Share(7, data).checksum());
        assertNotEquals(new Share(1, data).checksum(), new Share(1, "other bytes".getBytes()).checksum());
    }

    @Test
    void anEmptyShareStillHasAChecksum() {
        assertEquals(6, new Share(1, new byte[0]).checksum().length());
        assertEquals("", new Share(1, new byte[0]).hex());
    }

    @Test
    void aShareSetIsIndexedByTheNumberPrintedOnTheCard() {
        ShareSet set = new ShareSet(SplitScheme.of(3, 2), List.of(
                new Share(1, new byte[] {1}),
                new Share(2, new byte[] {2}),
                new Share(3, new byte[] {3})));

        assertEquals(3, set.size());
        assertArrayEquals(new byte[] {2}, set.get(2).data());
    }

    @Test
    void askingForAShareThatIsNotThereIsRejected() {
        ShareSet set = new ShareSet(SplitScheme.single(), List.of(new Share(1, new byte[] {1})));

        assertThrows(IllegalArgumentException.class, () -> set.get(2));
    }

    @Test
    void aShareSetDoesNotFollowLaterChangesToTheListItWasGiven() {
        List<Share> mutable = new java.util.ArrayList<>(List.of(new Share(1, new byte[] {1})));
        ShareSet set = new ShareSet(SplitScheme.single(), mutable);

        mutable.add(new Share(2, new byte[] {2}));

        assertEquals(1, set.size());
    }
}
