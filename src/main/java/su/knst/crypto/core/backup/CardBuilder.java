package su.knst.crypto.core.backup;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import su.knst.crypto.core.render.CardImage;
import su.knst.crypto.core.render.QrCodec;
import su.knst.crypto.core.secret.SecretType;
import su.knst.crypto.core.shamir.SecretSplitter;
import su.knst.crypto.core.shamir.Share;
import su.knst.crypto.core.shamir.ShareSet;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Decides which cards a backup actually gets: it drives the splitter, picks an error-correction
 * level, checks that every rendered card reads back as its own share, and retries or falls back
 * when it doesn't.
 *
 * The renderer itself neither verifies nor chooses - it draws what it is told to draw. Keeping the
 * judgement here is what lets one pipeline serve a Shamir split, a single whole-secret card and a
 * reprint, differing only in which {@link SecretSplitter} is handed in.
 */
public final class CardBuilder {
    /**
     * A Shamir split draws a fresh polynomial every call, so on the rare chance a particular
     * share's QR doesn't come back readable (barcode detection isn't perfectly reliable for every
     * bit pattern), redoing the whole split with fresh randomness is the real fix - not a weaker
     * error-correction level, and never handing back an unrestorable backup.
     */
    private static final int MAX_SPLIT_ATTEMPTS = 10;

    public record CardMeta(String backupName, SecretType type, LocalDate createdOn) {
    }

    /**
     * @param hasQr false when no error-correction level could fit the share, leaving the printed
     *              hex block as the only way back
     */
    public record CardSet(ShareSet shares, List<BufferedImage> images,
                          ErrorCorrectionLevel appliedLevel, boolean hasQr) {
        public CardSet {
            images = List.copyOf(images);
        }
    }

    private CardBuilder() {
    }

    public static CardSet build(SecretSplitter splitter, byte[] secret, CardMeta meta) throws BackupException {
        int attempts = splitter.randomized() ? MAX_SPLIT_ATTEMPTS : 1;
        ShareSet shares = null;

        for (int attempt = 0; attempt < attempts; attempt++) {
            shares = splitter.split(secret);

            Optional<CardSet> fitted = buildAtBestLevel(shares, meta);

            if (fitted.isPresent())
                return fitted.get();
        }

        return buildWithoutQr(shares, meta);
    }

    /**
     * Starts at the most error-correcting level and steps down a notch whenever any share is too
     * large to fit or renders a QR that doesn't decode back. One level applies to the whole set, so
     * every card in a backup is equally robust.
     */
    private static Optional<CardSet> buildAtBestLevel(ShareSet shares, CardMeta meta) {
        for (ErrorCorrectionLevel level : QrCodec.LEVELS) {
            List<BufferedImage> images = new ArrayList<>(shares.size());
            boolean levelFits = true;

            for (Share share : shares.shares()) {
                BufferedImage image;

                try {
                    image = CardImage.build(cardData(share, shares, meta, level, true));
                } catch (WriterException e) {
                    levelFits = false;
                    break;
                }

                // a share that renders but doesn't decode back to its own hex isn't safe to hand
                // out; verification runs in memory so share QR codes never touch the temp directory
                if (!share.hex().equals(QrCodec.decode(image))) {
                    levelFits = false;
                    break;
                }

                images.add(image);
            }

            if (levelFits)
                return Optional.of(new CardSet(shares, images, level, true));
        }

        return Optional.empty();
    }

    private static CardSet buildWithoutQr(ShareSet shares, CardMeta meta) throws BackupException {
        ErrorCorrectionLevel lowestLevel = QrCodec.LEVELS[QrCodec.LEVELS.length - 1];
        List<BufferedImage> images = new ArrayList<>(shares.size());

        for (Share share : shares.shares()) {
            try {
                images.add(CardImage.build(cardData(share, shares, meta, lowestLevel, false)));
            } catch (WriterException e) {
                throw new BackupException("Chunk " + share.index() + " failed to render: " + e.getMessage(), e);
            }
        }

        return new CardSet(shares, images, lowestLevel, false);
    }

    private static CardImage.ShareCardData cardData(Share share, ShareSet shares, CardMeta meta,
                                                    ErrorCorrectionLevel level, boolean showQr) {
        return new CardImage.ShareCardData(
                meta.backupName(),
                share.index(),
                shares.scheme().total(),
                shares.scheme().threshold(),
                meta.createdOn(),
                share.hex(),
                share.checksum(),
                meta.type().label(),
                level,
                showQr);
    }
}
