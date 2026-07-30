package su.knst.crypto.core.shamir;

import java.util.List;

/** The shares a {@link SecretSplitter} produced, in index order. */
public record ShareSet(SplitScheme scheme, List<Share> shares) {
    public ShareSet {
        shares = List.copyOf(shares);
    }

    public int size() {
        return shares.size();
    }

    /** @param index 1-based, matching the numbering printed on the cards */
    public Share get(int index) {
        return shares.stream()
                .filter(share -> share.index() == index)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No share with index " + index));
    }
}
