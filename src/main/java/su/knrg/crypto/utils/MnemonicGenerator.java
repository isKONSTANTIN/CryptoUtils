package su.knrg.crypto.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class MnemonicGenerator {
    protected static int next11Bits(byte[] bytes, int offset) {
        final int skip = offset / 8;
        final int lowerBitsToRemove = (3 * 8 - 11) - (offset % 8);
        return (((int) bytes[skip] & 0xff) << 16 |
                ((int) bytes[skip + 1] & 0xff) << 8 |
                (lowerBitsToRemove < 8
                        ? ((int) bytes[skip + 2] & 0xff)
                        : 0)) >> lowerBitsToRemove & (1 << 11) - 1;
    }

    public static String[] createMnemonic(byte[] entropy) throws NoSuchAlgorithmException {
        final int ent = entropy.length * 8;
        if (ent < 128)
            throw new RuntimeException("Entropy too low, 128-256 bits allowed");
        if (ent > 256)
            throw new RuntimeException("Entropy too high, 128-256 bits allowed");
        if (ent % 32 > 0)
            throw new RuntimeException("Number of entropy bits must be divisible by 32");

        final int cs = ent / 32;
        final int ms = (ent + cs) / 11;

        final byte[] entropyWithChecksum = Arrays.copyOf(entropy, entropy.length + 1);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] hash = digest.digest(entropy);
        entropyWithChecksum[entropy.length] = hash[0];

        String[] words = new String[ms];

        for (int i = 0; i < ent + cs; i += 11) {
            final int wordIndex = next11Bits(entropyWithChecksum, i);

            words[i / 11] = BIP39.ARRAY[wordIndex];
        }

        return words;
    }
}
