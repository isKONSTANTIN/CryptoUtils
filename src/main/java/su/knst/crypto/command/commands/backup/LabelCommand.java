package su.knst.crypto.command.commands.backup;

import com.google.zxing.WriterException;
import su.knst.crypto.Main;
import su.knst.crypto.command.ArgSource;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.CommandResultBuilder;
import su.knst.crypto.command.InteractiveArgSource;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.ScriptedArgSource;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.FileUtils;
import su.knst.crypto.utils.codes.ShareCardImage;
import su.knst.crypto.utils.codes.TagImage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

// Renders a single container tag (see TagImage) purely from text, without being tied to a
// Shamir backup - the barcode/checksum text is whatever the caller supplies as-is.
public class LabelCommand extends Command {
    private static final int PNG_DPI = 300;

    @Override
    public CommandResult run(ParamsContainer args) {
        ArgSource in = args.size() == 0
                ? new InteractiveArgSource(Main.getTerminalWorker())
                : new ScriptedArgSource(args);

        return resolve(in);
    }

    private CommandResult resolve(ArgSource in) {
        Optional<String> oTagName = in.string("Name for this label?");

        if (oTagName.isEmpty())
            return CommandResult.error("No input");

        Optional<Integer> oShareIndex = in.integer("Share index?");

        if (oShareIndex.isEmpty())
            return CommandResult.error("No input");

        Optional<Integer> oTotalShares = in.integer("Total number of shares?");

        if (oTotalShares.isEmpty())
            return CommandResult.error("No input");

        Optional<String> oText = in.restOfLine("Text for the barcode/checksum field?");

        if (oText.isEmpty())
            return CommandResult.error("No input");

        return finish(oTagName.get(), oShareIndex.get(), oTotalShares.get(), oText.get());
    }

    private CommandResult finish(String tagName, int shareIndex, int totalShares, String text) {
        BufferedImage image;

        try {
            image = TagImage.build(new TagImage.TagData(tagName, shareIndex, totalShares, text));
        } catch (WriterException e) {
            return CommandResult.error("Label failed to render: " + e.getMessage());
        }

        Path path = Main.getCurrentPath().resolve(tagName + "_tag_" + shareIndex + ".png");

        try {
            FileUtils.createOwnerOnly(path);
            ShareCardImage.writePng(image, path, PNG_DPI, TagImage.RENDER_SCALE);
        } catch (IOException e) {
            return CommandResult.error("Failed to write label file: " + e.getMessage());
        }

        return CommandResultBuilder.builder()
                .line("Label created: " + shareIndex + "/" + totalShares)
                .line(path.getFileName().toString())
                .build();
    }

    @Override
    public String description() {
        return "Render a single container tag/label from text only";
    }

    @Override
    public String args() {
        return "<tag_name> <share_index> <total_shares> <checksum_text>";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.BACKUPS;
    }
}
