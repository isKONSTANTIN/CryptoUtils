package su.knst.crypto.core.backup;

import su.knst.crypto.core.render.PngWriter;
import su.knst.crypto.core.shamir.Share;
import su.knst.crypto.utils.FileUtils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The one place backup files reach the disk, so the owner-only guarantee lives in a single code
 * path instead of being re-implemented per artifact kind.
 */
public final class ArtifactWriter {
    public record Written(List<Path> cardFiles, List<Path> tagFiles, List<Path> hexFiles) {
        public Written {
            cardFiles = List.copyOf(cardFiles);
            tagFiles = List.copyOf(tagFiles);
            hexFiles = List.copyOf(hexFiles);
        }
    }

    private ArtifactWriter() {
    }

    public static Written write(Path directory, String name, CardBuilder.CardSet cards, List<BufferedImage> tags)
            throws BackupException {
        try {
            List<Path> cardFiles = writeCards(directory, name, cards);
            List<Path> tagFiles = writeTags(directory, name, cards, tags);
            List<Path> hexFiles = writeHexFallback(directory, name, cards);

            return new Written(cardFiles, tagFiles, hexFiles);
        } catch (IOException e) {
            throw new BackupException("Failed to write backup files: " + e.getMessage(), e);
        }
    }

    private static List<Path> writeCards(Path directory, String name, CardBuilder.CardSet cards) throws IOException {
        List<Path> written = new ArrayList<>();
        List<Share> shares = cards.shares().shares();

        for (int i = 0; i < shares.size(); i++) {
            Path path = directory.resolve(name + "_" + shares.get(i).index() + ".png");

            PngWriter.writeOwnerOnly(cards.images().get(i), path);
            written.add(path);
        }

        return written;
    }

    private static List<Path> writeTags(Path directory, String name, CardBuilder.CardSet cards,
                                        List<BufferedImage> tags) throws IOException {
        List<Path> written = new ArrayList<>();
        List<Share> shares = cards.shares().shares();

        for (int i = 0; i < tags.size(); i++) {
            Path path = directory.resolve(name + "_tag_" + shares.get(i).index() + ".png");

            PngWriter.writeOwnerOnly(tags.get(i), path);
            written.add(path);
        }

        return written;
    }

    /**
     * When no error-correction level could fit the share, the card carries a hex block and nothing
     * scannable. Retyping a multi-kilobyte hex dump by hand is not a recovery plan, so the same hex
     * also goes out as a file - restore accepts pasted hex, which closes the loop.
     */
    private static List<Path> writeHexFallback(Path directory, String name, CardBuilder.CardSet cards)
            throws IOException {
        if (cards.hasQr())
            return List.of();

        List<Path> written = new ArrayList<>();

        for (Share share : cards.shares().shares()) {
            Path path = directory.resolve(name + "_" + share.index() + ".hex");

            FileUtils.writeOwnerOnly(path, share.hex());
            written.add(path);
        }

        return written;
    }
}
