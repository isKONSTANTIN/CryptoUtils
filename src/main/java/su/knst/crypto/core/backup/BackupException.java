package su.knst.crypto.core.backup;

/** A backup could not be produced - always with a message fit to show the user. */
public class BackupException extends Exception {
    public BackupException(String message) {
        super(message);
    }

    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}
