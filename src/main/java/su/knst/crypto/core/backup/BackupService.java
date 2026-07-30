package su.knst.crypto.core.backup;

import su.knst.crypto.core.secret.GzipCodec;
import su.knst.crypto.core.secret.SecretException;
import su.knst.crypto.core.shamir.SecretSplitter;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Composes the whole backup: read the secret, compress it, split it, render and verify the cards,
 * render the tags, write everything out, then lay out print sheets.
 *
 * Whether the secret is Shamir-split, printed whole on one card, or reprinted for an existing share
 * is entirely a property of the {@link SecretSplitter} that comes in - the steps do not branch.
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
        byte[] secret;

        try {
            secret = splitter.compress() ? GzipCodec.compress(payload) : payload;
        } catch (SecretException e) {
            throw new BackupException(e.getMessage(), e);
        }

        CardBuilder.CardSet cards = CardBuilder.build(splitter, secret, new CardBuilder.CardMeta(
                request.name(), request.source().type(), LocalDate.now()));

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
                request.source().type(),
                cards.appliedLevel(),
                cards.hasQr(),
                written.cardFiles(),
                written.tagFiles(),
                written.hexFiles(),
                sheets.files(),
                sheets.failure());
    }
}
