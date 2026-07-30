package su.knst.crypto.core.seed;

/**
 * One piece of entropy in every representation the seed command shows.
 *
 * @param mnemonic24 null when the entropy is too short for a 24-word phrase (needs 32 bytes)
 */
public record SeedView(byte[] entropy, String base64, String hex, String[] mnemonic12, String[] mnemonic24) {
    public boolean hasLongPhrase() {
        return mnemonic24 != null;
    }
}
