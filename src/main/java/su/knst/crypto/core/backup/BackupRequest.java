package su.knst.crypto.core.backup;

import su.knst.crypto.core.secret.SecretSource;
import su.knst.crypto.core.shamir.SecretSplitter;

import java.nio.file.Path;

/**
 * @param tagName   name printed on the container tags, or null to skip printing tags. Deliberately
 *                  separate from the backup name: one identifies the backup, the other the
 *                  physical container a share lives in
 * @param directory where the artifacts are written; commands resolve this, core never guesses it
 */
public record BackupRequest(String name, String tagName, SecretSplitter splitter,
                            SecretSource source, Path directory) {
}
