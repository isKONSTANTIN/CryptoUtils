package su.knst.crypto.utils;

import su.knst.crypto.command.CommandResult;
import su.knst.crypto.utils.exceptions.WrongMnemonicException;
import su.knst.crypto.utils.worldlists.WordLists;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.BitSet;

public class MnemonicUtils {
    protected static int next11Bits(byte[] bytes, int offset) {
        final int skip = offset / 8;
        final int lowerBitsToRemove = (3 * 8 - 11) - (offset % 8);
        return (((int) bytes[skip] & 0xff) << 16 |
                ((int) bytes[skip + 1] & 0xff) << 8 |
                (lowerBitsToRemove < 8
                        ? ((int) bytes[skip + 2] & 0xff)
                        : 0)) >> lowerBitsToRemove & (1 << 11) - 1;
    }

    public static byte[] entropyFromMnemonic(String[] words) {
        byte[] withChecksum = fromMnemonic(words);

        return Arrays.copyOf(withChecksum, withChecksum.length - 1);
    }

    public static byte[] fromMnemonic(String[] words) {
        int resultSize = (int)Math.ceil(words.length * 11 / 8f); // using full byte for checksum

        int wordI = 0;
        BitSet bits = new BitSet();

        for (String word : words) {
            int index = WordLists.getActiveList().getIndex(word).orElseThrow();

            for (int k = 0; k < 11; k++) {
                int bitInSet = (11 * wordI) + k;
                bits.set(bitInSet, isBitSet(index, 10 - k));
            }
            wordI++;
        }

        byte[] result = new byte[resultSize];
        int i = 0;
        for (byte b : bits.toByteArray()) {
            if (i == resultSize)
                break;

            result[i] = reverseBits(b);
            i++;
        }

        return result;
    }

    protected static byte reverseBits(byte x) {
        byte b = 0;
        for (int i = 0; i < 8; ++i) {
            b<<=1;
            b|=( x &1);
            x>>=1;
        }
        return b;
    }

    private static boolean isBitSet(int n, int k) {
        return ((n >> k) & 1) == 1;
    }

    public static byte[] sha256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        return digest.digest(data);
    }

    public static void checkMnemonic(String[] mnemonic) throws WrongMnemonicException, NoSuchAlgorithmException {
        int mnemonicLength = mnemonic.length;

        if (mnemonicLength != 12 && mnemonicLength != 24)
            throw new WrongMnemonicException("Wrong mnemonic size: " + mnemonicLength + " words not supported");

        WordLists.WordList wordList = WordLists.getActiveList();

        for (String word : mnemonic) {
            if (wordList.getIndex(word).isEmpty())
                throw new WrongMnemonicException("'" + word + "' not found in " + wordList.name() + " list");
        }

        byte[] entropyWithChecksum = fromMnemonic(mnemonic);
        byte[] hash = sha256(Arrays.copyOf(entropyWithChecksum, entropyWithChecksum.length - 1)); // calculate only entropy hash

        byte hashMask = (byte)(Byte.MAX_VALUE - (mnemonicLength == 12 ? 15 : 0)); // if 12-word seed byte mask is 11110000, else - 11111111

        if ((entropyWithChecksum[entropyWithChecksum.length - 1] & hashMask) != (hash[0] & hashMask))
            throw new WrongMnemonicException("Hash sum not valid");
    }

    public static String[] createMnemonic(byte[] entropy) throws NoSuchAlgorithmException {
        final int entropyBits = entropy.length * 8;
        if (entropyBits < 128)
            throw new RuntimeException("Entropy too low, 128-256 bits allowed");
        if (entropyBits > 256)
            throw new RuntimeException("Entropy too high, 128-256 bits allowed");
        if (entropyBits % 32 > 0)
            throw new RuntimeException("Number of entropy bits must be divisible by 32");

        final int checksumSize = entropyBits / 32;
        final int mnemonicSize = (entropyBits + checksumSize) / 11;

        final byte[] entropyWithChecksum = Arrays.copyOf(entropy, entropy.length + 1);
        final byte[] hash = sha256(entropy);

        entropyWithChecksum[entropy.length] = hash[0];

        String[] words = new String[mnemonicSize];

        for (int i = 0; i < entropyBits + checksumSize; i += 11) {
            final int wordIndex = next11Bits(entropyWithChecksum, i);

            words[i / 11] = WordLists.getActiveList().getWord(wordIndex).orElseThrow();
        }

        return words;
    }
}
