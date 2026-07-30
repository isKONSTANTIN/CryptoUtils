package su.knst.crypto.tests.core.seed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import su.knst.crypto.core.seed.SeedException;
import su.knst.crypto.core.seed.SeedService;
import su.knst.crypto.core.seed.SeedView;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.worldlists.WordLists;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SeedServiceTest {

    static final SecureRandom RANDOM = new SecureRandom();

    static byte[] randomEntropy(int bytes) {
        byte[] entropy = new byte[bytes];
        RANDOM.nextBytes(entropy);

        return entropy;
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 32})
    void entropyRoundTripsThroughAMnemonic(int entropyBytes) throws SeedException {
        byte[] entropy = randomEntropy(entropyBytes);

        String[] words = SeedService.fromEntropy(entropy);

        assertEquals(entropyBytes == 16 ? 12 : 24, words.length);
        assertArrayEquals(entropy, SeedService.toEntropy(words));
    }

    @Test
    void randomEntropyIsTheRequestedLengthAndNotConstant() {
        byte[] first = SeedService.randomEntropy(32);
        byte[] second = SeedService.randomEntropy(32);

        assertEquals(32, first.length);
        assertFalse(Arrays.equals(first, second));
    }

    @Test
    void aWordOutsideTheDictionaryIsRejected() throws SeedException {
        String[] words = SeedService.fromEntropy(randomEntropy(16));
        words[3] = "definitelynotabip39word";

        assertThrows(SeedException.class, () -> SeedService.toEntropy(words));
    }

    @Test
    void aBrokenChecksumIsRejected() {
        // all-zero entropy's phrase is "abandon" x11 + "about"; every word here is a real BIP-39
        // word and the length is right, so only the checksum can reject it
        String[] words = new String[12];
        Arrays.fill(words, "abandon");

        assertThrows(SeedException.class, () -> SeedService.toEntropy(words));
    }

    /**
     * A BIP-39 checksum is ENT/32 bits long - 4 bits for a 12-word phrase, 8 for a 24-word one - so
     * corrupting a phrase is caught with probability 1 - 2^-CS, not always. Swapping one word for
     * another changes the entropy while leaving the phrase's own checksum bits alone, and 1 in 16
     * such corruptions of a 12-word phrase still checks out (1 in 256 for 24 words).
     * <p>
     * That rate is the point: it is what tells us the checksum is the length it should be. The
     * sample is driven by a fixed seed so the measurement is repeatable rather than occasionally
     * red for no reason.
     */
    @ParameterizedTest(name = "{0} words: about 1 in {1} corruptions slips through")
    @CsvSource({"12, 16", "24, 256"})
    void corruptionSlipsThroughAtTheRateTheChecksumLengthAllows(int wordCount, int oneIn) throws SeedException {
        int samples = 50000;
        Random random = new Random(20260730L + wordCount);

        String[] source = SeedService.fromEntropy(
                randomEntropy(wordCount == 12 ? SeedService.SHORT_PHRASE_ENTROPY : SeedService.FULL_PHRASE_ENTROPY));

        int accepted = 0;

        for (int i = 0; i < samples; i++) {
            String[] corrupted = source.clone();

            // never the last word: that one carries the checksum bits themselves, and replacing it
            // would test something else entirely
            int position = random.nextInt(corrupted.length - 1);
            String replacement = randomWord(random);

            if (replacement.equals(corrupted[position]))
                continue;

            corrupted[position] = replacement;

            try {
                SeedService.toEntropy(corrupted);
                accepted++;
            } catch (SeedException expected) {
                // the normal case
            }
        }

        double rate = (double) accepted / samples;
        double expected = 1.0 / oneIn;

        // +-40% of the expected rate. At this sample size that is over 5 standard deviations even
        // for the rarer 24-word case, so the measurement is never marginal - while a checksum one
        // bit shorter or longer would double or halve the rate and fail outright.
        assertEquals(expected, rate, expected * 0.4,
                "expected about 1 in " + oneIn + " corruptions to pass, got " + accepted + " of " + samples);
    }

    static String randomWord(Random random) {
        return WordLists.getActiveList().array()[random.nextInt(WordLists.getActiveList().array().length)];
    }

    @Test
    void theSamePhraseWithAValidChecksumIsAccepted() throws SeedException {
        String[] words = new String[12];
        Arrays.fill(words, "abandon");
        words[11] = "about";

        assertArrayEquals(new byte[16], SeedService.toEntropy(words));
    }

    @Test
    void extendingGrows12WordsInto24ThatStillStartTheSame() throws SeedException {
        byte[] entropy = randomEntropy(16);

        byte[] extended = SeedService.extend(entropy);

        assertEquals(32, extended.length);
        assertArrayEquals(entropy, Arrays.copyOf(extended, 16));

        String[] short12 = SeedService.fromEntropy(entropy);
        String[] long24 = SeedService.fromEntropy(extended);

        assertEquals(12, short12.length);
        assertEquals(24, long24.length);
    }

    @Test
    void extendingIsDeterministic() throws SeedException {
        byte[] entropy = randomEntropy(16);

        assertArrayEquals(SeedService.extend(entropy), SeedService.extend(entropy));
    }

    @Test
    void extendingRejectsAnythingButSixteenBytes() {
        assertThrows(SeedException.class, () -> SeedService.extend(randomEntropy(32)));
        assertThrows(SeedException.class, () -> SeedService.extend(randomEntropy(8)));
    }

    @Test
    void describeShowsEveryEncodingOfTheSameEntropy() throws SeedException {
        byte[] entropy = randomEntropy(32);

        SeedView view = SeedService.describe(entropy);

        assertArrayEquals(entropy, view.entropy());
        assertEquals(Base64.getEncoder().encodeToString(entropy), view.base64());
        assertEquals(HexUtils.bytesToHex(entropy), view.hex());
        assertEquals(view.hex(), view.hex().toUpperCase());
        assertEquals(entropy.length * 2, view.hex().length());
    }

    @Test
    void describeOffersBothPhrasesForFullEntropy() throws SeedException {
        SeedView view = SeedService.describe(randomEntropy(32));

        assertEquals(12, view.mnemonic12().length);
        assertEquals(24, view.mnemonic24().length);
        assertTrue(view.hasLongPhrase());
    }

    @Test
    void describeOffersOnlyTheShortPhraseForShortEntropy() throws SeedException {
        SeedView view = SeedService.describe(randomEntropy(16));

        assertEquals(12, view.mnemonic12().length);
        assertNull(view.mnemonic24());
        assertFalse(view.hasLongPhrase());
    }

    @Test
    void describeRejectsEntropyTooShortForAPhrase() {
        assertThrows(SeedException.class, () -> SeedService.describe(randomEntropy(15)));
        assertThrows(SeedException.class, () -> SeedService.describe(new byte[0]));
    }

    @Test
    void zeroEntropyIsAllZeroHex() throws SeedException {
        SeedView view = SeedService.describe(new byte[16]);

        assertEquals("0".repeat(32), view.hex());
    }
}
