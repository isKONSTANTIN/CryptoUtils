package su.knst.crypto.core.shamir;

import com.codahale.shamir.Scheme;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reconstructs a secret from however many shares the user managed to bring back. */
public final class SecretJoiner {
    private SecretJoiner() {
    }

    public static byte[] join(List<Share> shares) {
        if (shares.isEmpty())
            throw new IllegalArgumentException("No shares provided");

        Map<Integer, byte[]> parts = new HashMap<>();

        for (Share share : shares)
            parts.put(share.index(), share.data());

        // the scheme's own n/k are only used by its constructor's validation (k > 1, n >= k) -
        // join() reconstructs purely from the given parts map, so these values don't need to match
        // the original split's n/k, just be valid
        return new Scheme(new SecureRandom(), Math.max(2, parts.size()), 2).join(parts);
    }
}
