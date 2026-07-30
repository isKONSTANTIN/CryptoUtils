package su.knst.crypto.core.shamir;

/**
 * How many shares a secret is split into and how many of them bring it back.
 *
 * @param total     number of shares produced (N)
 * @param threshold number of shares required to reconstruct (K)
 */
public record SplitScheme(int total, int threshold) {
    public static final int MAX_TOTAL = 255;

    public SplitScheme {
        if (threshold < 1)
            throw new IllegalArgumentException("Parts for recover must be >= 1");

        if (total < threshold)
            throw new IllegalArgumentException("All parts must be >= parts for recover");

        if (total > MAX_TOTAL)
            throw new IllegalArgumentException("All parts must be <= " + MAX_TOTAL);
    }

    /** A real Shamir split. Recovering from a single share would defeat the point, so K >= 2. */
    public static SplitScheme of(int total, int threshold) {
        if (threshold < 2)
            throw new IllegalArgumentException("Parts for recover must be >= 2");

        return new SplitScheme(total, threshold);
    }

    /** One card carrying the whole secret, with nothing split off. */
    public static SplitScheme single() {
        return new SplitScheme(1, 1);
    }

    public boolean isSplit() {
        return total > 1;
    }
}
