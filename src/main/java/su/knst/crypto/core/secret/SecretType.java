package su.knst.crypto.core.secret;

/**
 * What kind of thing a backup holds. Printed on the card so a finder knows what they have, and the
 * one place that decides whether the payload is worth compressing.
 */
public enum SecretType {
    /** Arbitrary file contents, which usually compress. */
    FILE(true),
    /** Free text, which usually compresses. */
    TEXT(true),
    /**
     * BIP-39 entropy: 16 or 32 bytes of it, already as dense as it gets. Gzip would only add a
     * header and grow the QR payload.
     */
    SEED(false),
    /**
     * Raw share bytes, used when reprinting a card for a share that already exists. It has to reach
     * the card byte for byte or the reprint no longer combines with its siblings.
     */
    HEX(false);

    private final boolean compressed;

    SecretType(boolean compressed) {
        this.compressed = compressed;
    }

    /**
     * Whether a payload of this type is gzipped on the way onto a card, and gunzipped on the way
     * back off one. Compression belongs to the data, not to how it was split - a seed phrase is
     * printed the same way whether it went onto one card or five.
     */
    public boolean compressed() {
        return compressed;
    }

    public String label() {
        return name();
    }
}
