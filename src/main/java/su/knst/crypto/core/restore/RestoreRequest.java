package su.knst.crypto.core.restore;

import su.knst.crypto.core.secret.SecretSink;

import java.util.List;

/**
 * @param chunks the shares in the order they were numbered when the backup was made; a share the
 *               user no longer has is a {@link ShareInput.Skipped} slot, not a missing entry
 */
public record RestoreRequest(SecretSink sink, RestoreMode mode, List<ShareInput> chunks) {
    public RestoreRequest {
        chunks = List.copyOf(chunks);
    }
}
