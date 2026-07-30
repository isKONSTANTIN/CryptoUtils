package su.knst.crypto.core.backup;

import com.google.zxing.WriterException;
import su.knst.crypto.core.render.TagImage;
import su.knst.crypto.core.shamir.Share;
import su.knst.crypto.core.shamir.ShareSet;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Renders the container tag that goes with each card. */
public final class TagBuilder {
    private TagBuilder() {
    }

    public static List<BufferedImage> buildAll(ShareSet shares, String tagName) throws BackupException {
        List<BufferedImage> tags = new ArrayList<>(shares.size());

        for (Share share : shares.shares()) {
            try {
                tags.add(TagImage.build(new TagImage.TagData(
                        tagName, share.index(), shares.scheme().total(), share.checksum())));
            } catch (WriterException e) {
                throw new BackupException("Tag " + share.index() + " failed to render: " + e.getMessage(), e);
            }
        }

        return tags;
    }
}
