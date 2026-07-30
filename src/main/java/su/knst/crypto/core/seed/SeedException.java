package su.knst.crypto.core.seed;

/** A mnemonic or its entropy was rejected - always with a message fit to show the user. */
public class SeedException extends Exception {
    public SeedException(String message) {
        super(message);
    }

    public SeedException(String message, Throwable cause) {
        super(message, cause);
    }
}
