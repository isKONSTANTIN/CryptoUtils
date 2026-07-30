package su.knst.crypto.core.secret;

/** A secret could not be read, decoded or written - always with a message fit to show the user. */
public class SecretException extends Exception {
    public SecretException(String message) {
        super(message);
    }

    public SecretException(String message, Throwable cause) {
        super(message, cause);
    }
}
