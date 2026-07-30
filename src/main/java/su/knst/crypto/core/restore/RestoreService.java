package su.knst.crypto.core.restore;

import su.knst.crypto.core.secret.GzipCodec;
import su.knst.crypto.core.secret.SecretException;
import su.knst.crypto.core.secret.SecretSink;
import su.knst.crypto.core.shamir.SecretJoiner;
import su.knst.crypto.core.shamir.Share;

import java.util.ArrayList;
import java.util.List;

/** Reads the shares back off their cards, reassembles the secret and hands it to the sink. */
public final class RestoreService {
    public SecretSink.Written restore(RestoreRequest request) throws RestoreException {
        List<Share> shares = collect(request.chunks());

        if (shares.isEmpty())
            throw new RestoreException("No chunks provided");

        byte[] secret = switch (request.mode()) {
            case WHOLE -> whole(shares);
            case SHAMIR -> join(shares);
        };

        try {
            // the mirror of BackupService: whether the payload was compressed on the way in is a
            // property of what kind of secret it is, and the user names that when picking the sink
            if (request.sink().type().compressed())
                secret = GzipCodec.decompress(secret);

            return request.sink().write(secret);
        } catch (SecretException e) {
            throw new RestoreException(e.getMessage(), e);
        }
    }

    /**
     * The share's index is its position in the list, which is why a skipped slot still advances the
     * counter: shares combine by index, and shifting them up would reconstruct a different secret.
     */
    private static List<Share> collect(List<ShareInput> chunks) throws RestoreException {
        List<Share> shares = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            ShareInput input = chunks.get(i);

            if (input instanceof ShareInput.Skipped)
                continue;

            int index = i + 1;
            shares.add(new Share(index, ShareReader.read(input, index)));
        }

        return shares;
    }

    private static byte[] whole(List<Share> shares) throws RestoreException {
        if (shares.size() != 1)
            throw new RestoreException("An unsplit backup is a single card, but " + shares.size() + " were given");

        return shares.get(0).data();
    }

    private static byte[] join(List<Share> shares) throws RestoreException {
        try {
            return SecretJoiner.join(shares);
        } catch (IllegalArgumentException e) {
            throw new RestoreException("Failed to reconstruct secret: " + e.getMessage(), e);
        }
    }
}
