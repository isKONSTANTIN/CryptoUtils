package su.knst.crypto.core.shamir;

import com.codahale.shamir.Scheme;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ShamirSplitter implements SecretSplitter {
    private final SplitScheme scheme;

    ShamirSplitter(SplitScheme scheme) {
        if (!scheme.isSplit())
            throw new IllegalArgumentException("A Shamir split needs more than one part");

        this.scheme = scheme;
    }

    @Override
    public ShareSet split(byte[] secret) {
        Map<Integer, byte[]> parts =
                new Scheme(new SecureRandom(), scheme.total(), scheme.threshold()).split(secret);

        List<Share> shares = new ArrayList<>(scheme.total());

        for (int i = 1; i <= scheme.total(); i++)
            shares.add(new Share(i, parts.get(i)));

        return new ShareSet(scheme, shares);
    }

    @Override
    public SplitScheme scheme() {
        return scheme;
    }

    @Override
    public boolean randomized() {
        return true;
    }
}
