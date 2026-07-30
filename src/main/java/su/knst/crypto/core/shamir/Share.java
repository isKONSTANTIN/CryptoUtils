package su.knst.crypto.core.shamir;

import su.knst.crypto.utils.HexUtils;

import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * One share of a split secret, at its 1-based position in the set.
 *
 * The index is not carried inside the payload - it is printed on the card as text, and at restore
 * time it comes from the order the user lists the shares in.
 */
public record Share(int index, byte[] data) {
    private static final int CHECKSUM_LENGTH = 6;

    /** The QR payload: the share bytes as plain uppercase hex, with no header or framing. */
    public String hex() {
        return HexUtils.bytesToHex(data);
    }

    /** Short human-comparable fingerprint, printed on the card and encoded in its CODE128 barcode. */
    public String checksum() {
        try {
            return HexUtils.hexHash(data).substring(0, CHECKSUM_LENGTH).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the platform: absent here means a broken JRE, not a bad input
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
