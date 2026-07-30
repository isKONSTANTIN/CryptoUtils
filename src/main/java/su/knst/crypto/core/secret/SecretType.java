package su.knst.crypto.core.secret;

/** What kind of thing a backup holds. Printed on the card so a finder knows what they have. */
public enum SecretType {
    FILE,
    TEXT,
    SEED,
    /** Raw share bytes, used when reprinting a card for a share that already exists. */
    HEX;

    public String label() {
        return name();
    }
}
