package su.knst.crypto.core.restore;

/** A backup could not be reconstructed - always with a message fit to show the user. */
public class RestoreException extends Exception {
    public RestoreException(String message) {
        super(message);
    }

    public RestoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
