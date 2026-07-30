package su.knst.crypto.core.restore;

/**
 * Whether the cards on the table are Shamir shares or a single whole backup.
 *
 * The QR payload is plain hex with no header, so this cannot be told from the data - the user says
 * which it is, and nothing has to be guessed from the bytes.
 */
public enum RestoreMode {
    /** One card holding the entire secret, produced by a backup made without splitting. */
    WHOLE,
    /** Shamir shares, combined by index. */
    SHAMIR
}
