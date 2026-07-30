package su.knst.crypto.core.backup;

import su.knst.crypto.core.secret.GzipCodec;
import su.knst.crypto.core.secret.SecretException;
import su.knst.crypto.core.secret.SecretType;
import su.knst.crypto.core.shamir.SecretSplitter;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Composes the whole backup: read the secret, compress it if its type calls for that, split it,
 * render and verify the cards, render the tags, write everything out, then lay out print sheets.
 *
 * Whether the secret is Shamir-split, printed whole on one card, or reprinted for an existing share
 * is entirely a property of the {@link SecretSplitter} that comes in; whether it is compressed is
 * entirely a property of its {@link SecretType}. Neither makes the steps branch.
 */
public final class BackupService {
    public BackupResult create(BackupRequest request) throws BackupException {
        byte[] payload;

        try {
            payload = request.source().read();
        } catch (SecretException e) {
            throw new BackupException(e.getMessage(), e);
        }

        if (payload.length == 0)
            throw new BackupException("Source data is empty");

        SecretSplitter splitter = request.splitter();
        SecretType type = request.source().type();
        byte[] secret;

        try {
            secret = type.compressed() ? GzipCodec.compress(payload) : payload;
        } catch (SecretException e) {
            throw new BackupException(e.getMessage(), e);
        }

        CardBuilder.CardSet cards = CardBuilder.build(splitter, secret, new CardBuilder.CardMeta(
                request.name(), type, LocalDate.now()));

        List<BufferedImage> tags = request.tagName() == null
                ? List.of()
                : TagBuilder.buildAll(cards.shares(), request.tagName());

        ArtifactWriter.Written written = ArtifactWriter.write(request.directory(), request.name(), cards, tags);

        List<BufferedImage> printable = new ArrayList<>(cards.images());
        printable.addAll(tags);

        SheetBuilder.Outcome sheets = SheetBuilder.tryBuild(request.directory(), request.name(), printable);

        return new BackupResult(
                request.name(),
                cards.shares().scheme(),
                type,
                cards.appliedLevel(),
                cards.hasQr(),
                written.cardFiles(),
                written.tagFiles(),
                written.hexFiles(),
                sheets.files(),
                sheets.failure());
    }
}
