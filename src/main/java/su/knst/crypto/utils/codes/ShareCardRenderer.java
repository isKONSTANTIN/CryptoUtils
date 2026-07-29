package su.knst.crypto.utils.codes;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/**
 * Shared best-effort QR error-correction level selection for {@link ShareCardImage}: a share is
 * rendered at the most error-correcting level first, stepping down a notch whenever it doesn't fit
 * or the rendered QR doesn't decode back to its own payload, with a QR-less fallback if no level
 * works. Used both by the full backup split flow (which retries the whole split on decode failure)
 * and by standalone single-card generation (which cannot retry, since its input is fixed).
 */
public final class ShareCardRenderer {
    // Tried in order, most error-correction first; if a share is too large to fit a QR code at
    // one level, drop down a notch rather than giving up on the QR entirely.
    public static final ErrorCorrectionLevel[] ERROR_CORRECTION_LEVELS = {
            ErrorCorrectionLevel.H, ErrorCorrectionLevel.Q, ErrorCorrectionLevel.M, ErrorCorrectionLevel.L
    };

    // Standard QR EC strength ordering (weakest to strongest), used only to number levels in
    // describeLevel() — kept separate from ERROR_CORRECTION_LEVELS, whose order is fallback
    // preference (strongest first), not the ISO 18004 strength ranking.
    private static final ErrorCorrectionLevel[] STRENGTH_ORDER = {
            ErrorCorrectionLevel.L, ErrorCorrectionLevel.M, ErrorCorrectionLevel.Q, ErrorCorrectionLevel.H
    };

    public record Result(BufferedImage image, ErrorCorrectionLevel appliedLevel, boolean hasQr, boolean decodeFailed) {
    }

    private ShareCardRenderer() {
    }

    // Builds one card at a specific error-correction level and verifies it actually decodes back
    // to its own hex payload. Empty means this level doesn't work (too large, or unreliable decode).
    public static Optional<BufferedImage> tryBuildAtLevel(ShareCardImage.ShareCardData dataAtLevel) {
        BufferedImage image;

        try {
            image = ShareCardImage.build(dataAtLevel);
        } catch (WriterException e) {
            return Optional.empty();
        }

        if (!decodesTo(image, dataAtLevel.hexPayload()))
            return Optional.empty();

        return Optional.of(image);
    }

    // Picks the best-fitting error-correction level for a single card, falling back to a QR-less
    // card if none of ERROR_CORRECTION_LEVELS works.
    public static Result renderBestFit(String backupName, int shareIndex, int totalShares, int threshold,
                                        java.time.LocalDate createdOn, String hexPayload, String checksumHex,
                                        String typeLabel) throws WriterException {
        boolean decodeFailed = false;

        for (ErrorCorrectionLevel level : ERROR_CORRECTION_LEVELS) {
            ShareCardImage.ShareCardData data = new ShareCardImage.ShareCardData(
                    backupName, shareIndex, totalShares, threshold, createdOn, hexPayload, checksumHex,
                    typeLabel, level, true);

            Optional<BufferedImage> image = tryBuildAtLevel(data);

            if (image.isPresent())
                return new Result(image.get(), level, true, false);

            decodeFailed = true;
        }

        ErrorCorrectionLevel lowestLevel = ERROR_CORRECTION_LEVELS[ERROR_CORRECTION_LEVELS.length - 1];

        BufferedImage fallback = ShareCardImage.build(new ShareCardImage.ShareCardData(
                backupName, shareIndex, totalShares, threshold, createdOn, hexPayload, checksumHex,
                typeLabel, lowestLevel, false));

        return new Result(fallback, lowestLevel, false, decodeFailed);
    }

    public static String describeLevel(ErrorCorrectionLevel level) {
        String name = switch (level) {
            case L -> "Low";
            case M -> "Medium";
            case Q -> "Quartile";
            case H -> "High";
        };

        int position = Arrays.asList(STRENGTH_ORDER).indexOf(level) + 1;

        return name + " (" + position + "/" + STRENGTH_ORDER.length + ")";
    }

    // a share that renders but doesn't decode back to its own hex isn't safe to hand out
    public static boolean decodesTo(BufferedImage image, String expectedHex) {
        try {
            Path temp = Files.createTempFile("backup_qr_verify", ".png");

            try {
                ImageIO.write(image, "png", temp.toFile());

                return expectedHex.equals(new SimpleQRCodeWorker().readCode(temp.toString()));
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            return false;
        }
    }
}
