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
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.codes.ShareCardImage;
import su.knst.crypto.utils.codes.ShareCardRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Renders a single share card (see ShareCardImage) directly from a string/hex/file payload,
// without running a Shamir split first. Useful to reprint one lost or damaged card for an
// already-existing share, given its known hex value, or to produce an ad-hoc card for arbitrary
// data.
public class CardCommand extends Command {
    private static final List<Prompts.Choice> TYPE_CHOICES = List.of(
            new Prompts.Choice("text", "Text", "Type the payload in directly"),
            new Prompts.Choice("hex", "Hex", "Provide the payload as a hex string"),
            new Prompts.Choice("file", "File", "Read the payload from a file on disk")
    );
    private static final int PNG_DPI = 300;

    @Override
    public CommandResult run(ParamsContainer args) {
        ArgSource in = args.size() == 0
                ? new InteractiveArgSource(Main.getTerminalWorker())
                : new ScriptedArgSource(args);

        return resolve(in);
    }

    private CommandResult resolve(ArgSource in) {
        Optional<String> oType = in.choice("Card payload type?", TYPE_CHOICES);

        if (oType.isEmpty())
            return CommandResult.error("No input");

        String type = oType.get();

        Optional<String> oName = in.string("Name for this card?");

        if (oName.isEmpty())
            return CommandResult.error("No input");

        Optional<Integer> oShareIndex = in.integer("Share index?");

        if (oShareIndex.isEmpty())
            return CommandResult.error("No input");

        Optional<Integer> oTotalShares = in.integer("Total number of shares?");

        if (oTotalShares.isEmpty())
            return CommandResult.error("No input");

        Optional<Integer> oThreshold = in.integer("Parts required to recover (K)?");

        if (oThreshold.isEmpty())
            return CommandResult.error("No input");

        byte[] payload;

        switch (type) {
            case "text" -> {
                Optional<String> oText = in.restOfLine("Enter text for the card:");

                if (oText.isEmpty())
                    return CommandResult.error("No input");

                payload = oText.get().getBytes(StandardCharsets.UTF_8);
            }
            case "hex" -> {
                Optional<String> oHex = in.string("Enter hex payload:");

                if (oHex.isEmpty())
                    return CommandResult.error("No input");

                String hex = oHex.get().trim();

                if (!HexUtils.isValidHex(hex))
                    return CommandResult.error("Not a valid hex string");

                payload = HexUtils.hexStringToByteArray(hex);
            }
            case "file" -> {
                Optional<Path> oPath = in.existingFilePath("Path to file?");

                if (oPath.isEmpty())
                    return CommandResult.error("No input");

                try {
                    payload = Files.readAllBytes(oPath.get());
                } catch (IOException e) {
                    return CommandResult.error("Failed to read source file: " + e.getMessage());
                }
            }
            default -> {
                return CommandResult.error("Unknown type");
            }
        }

        return finish(type, oName.get(), oShareIndex.get(), oTotalShares.get(), oThreshold.get(), payload);
    }

    private CommandResult finish(String type, String name, int shareIndex, int totalShares, int threshold, byte[] payload) {
        if (payload.length == 0)
            return CommandResult.error("Payload is empty");

        // hex-encoded as-is (no gzip): for type=hex this keeps the card's payload byte-identical
        // to what was typed, which matters when reprinting a card for an already-existing share.
        String hexPayload = HexUtils.bytesToHex(payload);
        String checksumHex;

        try {
            checksumHex = HexUtils.hexHash(payload).substring(0, 6).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            return CommandResult.error("Failed to compute checksum: " + e.getMessage());
        }

        String typeLabel = type.toUpperCase();
        ShareCardRenderer.Result result;

        try {
            result = ShareCardRenderer.renderBestFit(
                    name, shareIndex, totalShares, threshold, LocalDate.now(), hexPayload, checksumHex, typeLabel);
        } catch (WriterException e) {
            return CommandResult.error("Card failed to render: " + e.getMessage());
        }

        Path path = Main.getCurrentPath().resolve(name + "_" + shareIndex + ".png");

        try {
            FileUtils.createOwnerOnly(path);
            ShareCardImage.writePng(result.image(), path, PNG_DPI);
        } catch (IOException e) {
            return CommandResult.error("Failed to write card file: " + e.getMessage());
        }

        CommandResultBuilder builder = CommandResultBuilder.builder();

        builder.line("Card created: " + shareIndex + "/" + totalShares + ", " + threshold + " required to recover")
                .line(result.hasQr()
                        ? "QR error correction level: " + ShareCardRenderer.describeLevel(result.appliedLevel())
                        : "QR code: none (payload too large at every error correction level, hex block only)");

        if (result.decodeFailed() && !result.hasQr())
            builder.line("Warning: the QR code did not reliably decode at any error correction level for this fixed payload; retrying would produce the same result, so the card was rendered without a QR code.");

        builder.line(path.getFileName().toString());

        return builder.build();
    }

    @Override
    public String description() {
        return "Render a single share card from a string, hex string or file, without running a Shamir split";
    }

    @Override
    public String args() {
        return "text|hex|file <name> <share_index> <total_shares> <threshold> <source...>";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.BACKUPS;
    }
}
