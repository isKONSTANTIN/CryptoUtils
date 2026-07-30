package su.knst.crypto.tests.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import su.knst.crypto.core.secret.GzipCodec;
import su.knst.crypto.core.seed.SeedService;
import su.knst.crypto.core.shamir.SecretJoiner;
import su.knst.crypto.core.shamir.SecretSplitter;
import su.knst.crypto.core.shamir.Share;
import su.knst.crypto.core.shamir.ShareSet;
import su.knst.crypto.core.shamir.SplitScheme;
import su.knst.crypto.utils.HexUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The whole seed-backup pipeline end to end, module by module:
 * <p>
 * entropy -&gt; mnemonic -&gt; back to entropy -&gt; gzip -&gt; Shamir split -&gt; hex payload as printed
 * on a card -&gt; back from hex -&gt; join from many different subsets of shares -&gt; gunzip -&gt;
 * mnemonic again, verifying every step round-trips to exactly the same data.
 * <p>
 * Runs across both entropy sizes and several Shamir schemes, and for each split joins from several
 * random subsets of the required size, so recovery cannot depend on which particular shares survive.
 */
class SeedBackupRoundTripTest {

    static final SecureRandom RANDOM = new SecureRandom();

    static Stream<Arguments> variants() {
        int[] entropySizes = {16, 32};
        int[][] schemes = {{2, 2}, {3, 2}, {5, 3}, {7, 4}, {5, 5}};

        List<Arguments> args = new ArrayList<>();

        for (int entropySize : entropySizes)
            for (int[] scheme : schemes)
                args.add(Arguments.of(entropySize, scheme[0], scheme[1]));

        return args.stream();
    }

    @ParameterizedTest(name = "{0}-byte entropy, shamir {1} parts / {2} required")
    @MethodSource("variants")
    void fullWorkflow(int entropySize, int allParts, int forRecover) throws Exception {
        int expectedWords = entropySize == 32 ? 24 : 12;

        // 1. a fresh seed phrase, as `seed generate` produces it
        byte[] sourceEntropy = SeedService.randomEntropy(entropySize);
        String[] mnemonic = SeedService.fromEntropy(sourceEntropy);

        assertEquals(expectedWords, mnemonic.length);

        // 2. the phrase typed back in reproduces exactly the entropy it came from
        assertArrayEquals(sourceEntropy, SeedService.toEntropy(mnemonic));

        // 3. compress and split, as `backup` does
        byte[] compressed = GzipCodec.compress(sourceEntropy);
        ShareSet shares = SecretSplitter.shamir(SplitScheme.of(allParts, forRecover)).split(compressed);

        assertEquals(allParts, shares.size());

        // 4. each share travels as the plain hex printed on its card
        List<Share> fromCards = shares.shares().stream()
                .map(share -> new Share(share.index(), HexUtils.hexStringToByteArray(share.hex())))
                .toList();

        for (int i = 0; i < fromCards.size(); i++)
            assertArrayEquals(shares.shares().get(i).data(), fromCards.get(i).data());

        // 5. join from several different random subsets of the required size
        for (int attempt = 0; attempt < 3; attempt++) {
            List<Share> subset = new ArrayList<>(fromCards);
            Collections.shuffle(subset, RANDOM);

            byte[] joined = SecretJoiner.join(subset.subList(0, forRecover));
            byte[] recovered = GzipCodec.decompress(joined);

            assertArrayEquals(sourceEntropy, recovered);
            assertArrayEquals(mnemonic, SeedService.fromEntropy(recovered));
        }
    }

    @ParameterizedTest(name = "{0}-byte entropy, shamir {1} parts / {2} required")
    @MethodSource("variants")
    void tooFewSharesDoNotReconstructTheSecret(int entropySize, int allParts, int forRecover) throws Exception {
        if (forRecover < 2 || allParts == forRecover && forRecover == 2)
            return;

        byte[] compressed = GzipCodec.compress(SeedService.randomEntropy(entropySize));
        ShareSet shares = SecretSplitter.shamir(SplitScheme.of(allParts, forRecover)).split(compressed);

        List<Share> tooFew = new ArrayList<>(shares.shares()).subList(0, forRecover - 1);
        byte[] joined = SecretJoiner.join(tooFew);

        assertFalse(java.util.Arrays.equals(compressed, joined),
                "fewer than K shares must not reconstruct the secret");
    }
}
