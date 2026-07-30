package su.knst.crypto.core.restore;

import java.nio.file.Path;

/**
 * One slot in the list of shares handed to a restore. Position in the list is the share's index, so
 * a share the user no longer has still occupies a slot as {@link Skipped}.
 */
public sealed interface ShareInput {
    /** A card image to scan the QR code out of. */
    record FromFile(Path path) implements ShareInput {
    }

    /** A hex payload read off the card by hand. */
    record FromHex(String hex) implements ShareInput {
    }

    /** A share that is not available; it still counts towards the numbering. */
    record Skipped() implements ShareInput {
    }
}
