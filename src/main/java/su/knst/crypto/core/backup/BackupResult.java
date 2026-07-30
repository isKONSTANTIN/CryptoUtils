package su.knst.crypto.core.backup;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import su.knst.crypto.core.secret.SecretType;
import su.knst.crypto.core.shamir.SplitScheme;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Everything a finished backup produced. Formatting this into console output is the command's job.
 *
 * @param hexFiles      plain-hex copies of the shares, written only when no QR code would fit
 * @param sheetFailure  why print sheets were skipped, or empty when they were produced
 */
public record BackupResult(String name, SplitScheme scheme, SecretType type,
                           ErrorCorrectionLevel appliedLevel, boolean hasQr,
                           List<Path> cardFiles, List<Path> tagFiles, List<Path> hexFiles,
                           List<Path> sheetFiles, Optional<String> sheetFailure) {
    public BackupResult {
        cardFiles = List.copyOf(cardFiles);
        tagFiles = List.copyOf(tagFiles);
        hexFiles = List.copyOf(hexFiles);
        sheetFiles = List.copyOf(sheetFiles);
    }
}
