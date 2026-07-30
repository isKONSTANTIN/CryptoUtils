package su.knst.crypto.core.shamir;

/**
 * Turns a secret into the set of shares that will be printed as cards.
 *
 * Backing up without splitting is not a separate code path - it is an implementation of this
 * interface that hands back a single share, so the pipeline above it stays the same whether the
 * user asked for a Shamir split, one whole card, or a reprint of a share they already hold.
 */
public interface SecretSplitter {
    ShareSet split(byte[] secret);

    SplitScheme scheme();

    /**
     * True when split() draws fresh randomness, so re-running it produces different share bytes.
     * Only then is it worth retrying a split whose card failed to decode back.
     */
    boolean randomized();

    /** False when the payload must reach the card byte-for-byte, with no compression applied. */
    boolean compress();

    static SecretSplitter shamir(SplitScheme scheme) {
        return new ShamirSplitter(scheme);
    }

    static SecretSplitter single() {
        return new SingleShareSplitter();
    }

    /**
     * Re-renders a card for a share that already exists, given its known payload. The share keeps
     * its original index within its original scheme, and the payload is passed through untouched -
     * anything else would produce a card that no longer combines with its siblings.
     */
    static SecretSplitter reprint(int index, SplitScheme scheme) {
        return new ReprintSplitter(index, scheme);
    }
}
