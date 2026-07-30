package su.knst.crypto.core.shamir;

import java.util.List;

/** No split at all: one card holds the whole secret. */
final class SingleShareSplitter implements SecretSplitter {
    @Override
    public ShareSet split(byte[] secret) {
        return new ShareSet(SplitScheme.single(), List.of(new Share(1, secret)));
    }

    @Override
    public SplitScheme scheme() {
        return SplitScheme.single();
    }

    @Override
    public boolean randomized() {
        return false;
    }
}
