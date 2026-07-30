package su.knst.crypto.core.shamir;

import java.util.List;

/** Re-renders one already-existing share onto a fresh card, payload untouched. */
final class ReprintSplitter implements SecretSplitter {
    private final int index;
    private final SplitScheme scheme;

    ReprintSplitter(int index, SplitScheme scheme) {
        if (index < 1 || index > scheme.total())
            throw new IllegalArgumentException("Share index must be between 1 and " + scheme.total());

        this.index = index;
        this.scheme = scheme;
    }

    @Override
    public ShareSet split(byte[] secret) {
        return new ShareSet(scheme, List.of(new Share(index, secret)));
    }

    @Override
    public SplitScheme scheme() {
        return scheme;
    }

    @Override
    public boolean randomized() {
        return false;
    }

    @Override
    public boolean compress() {
        // the payload is an existing share's bytes; compressing them would make the reprinted card
        // fail to combine with the siblings it was printed to replace
        return false;
    }
}
