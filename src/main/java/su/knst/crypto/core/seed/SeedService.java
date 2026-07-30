package su.knst.crypto.core.seed;

import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.MnemonicUtils;
import su.knst.crypto.utils.exceptions.WrongMnemonicException;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** BIP-39 entropy and mnemonic conversions, independent of how they get shown. */
public final class SeedService {
    public static final int SHORT_PHRASE_ENTROPY = 16;
    public static final int FULL_PHRASE_ENTROPY = 32;

    private SeedService() {
    }

    public static byte[] randomEntropy(int bytes) {
        byte[] entropy = new byte[bytes];
        new SecureRandom().nextBytes(entropy);

        return entropy;
    }

    public static String[] fromEntropy(byte[] entropy) throws SeedException {
        try {
            return MnemonicUtils.createMnemonic(entropy);
        } catch (RuntimeException e) {
            throw new SeedException("Failed to build mnemonic: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static byte[] toEntropy(String[] words) throws SeedException {
        try {
            MnemonicUtils.checkMnemonic(words);
        } catch (WrongMnemonicException e) {
            throw new SeedException("Failed to check mnemonic: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }

        return MnemonicUtils.entropyFromMnemonic(words);
    }

    /**
     * Grows 16 bytes of entropy to 32 by appending the first half of its own SHA-256, turning a
     * 12-word phrase into a 24-word one that the original 12 words still reconstruct.
     */
    public static byte[] extend(byte[] entropy) throws SeedException {
        if (entropy.length != SHORT_PHRASE_ENTROPY)
            throw new SeedException("Extending needs exactly " + SHORT_PHRASE_ENTROPY + " bytes of entropy");

        byte[] hash;

        try {
            hash = MnemonicUtils.sha256(entropy);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }

        byte[] extended = Arrays.copyOf(entropy, FULL_PHRASE_ENTROPY);
        System.arraycopy(hash, 0, extended, SHORT_PHRASE_ENTROPY, SHORT_PHRASE_ENTROPY);

        return extended;
    }

    public static SeedView describe(byte[] entropy) throws SeedException {
        if (entropy.length < SHORT_PHRASE_ENTROPY)
            throw new SeedException("Not enough source entropy!\n"
                    + "Given: " + entropy.length + " bytes\n"
                    + "Min: " + SHORT_PHRASE_ENTROPY + " bytes");

        String[] mnemonic12 = fromEntropy(Arrays.copyOf(entropy, SHORT_PHRASE_ENTROPY));
        String[] mnemonic24 = entropy.length >= FULL_PHRASE_ENTROPY
                ? fromEntropy(Arrays.copyOf(entropy, FULL_PHRASE_ENTROPY))
                : null;

        return new SeedView(
                entropy,
                Base64.getEncoder().encodeToString(entropy),
                HexUtils.bytesToHex(entropy),
                mnemonic12,
                mnemonic24);
    }
}
