package su.knst.crypto.command.commands.backup;

import su.knst.crypto.Main;
import su.knst.crypto.command.ArgSource;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.CommandResultBuilder;
import su.knst.crypto.command.InteractiveArgSource;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.ScriptedArgSource;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.core.backup.BackupException;
import su.knst.crypto.core.backup.BackupRequest;
import su.knst.crypto.core.backup.BackupResult;
import su.knst.crypto.core.backup.BackupService;
import su.knst.crypto.core.render.QrCodec;
import su.knst.crypto.core.secret.SecretSource;
import su.knst.crypto.core.shamir.SecretSplitter;
import su.knst.crypto.core.shamir.SplitScheme;
import su.knst.crypto.utils.Prompts;

import java.util.List;
import java.util.Optional;

// Renders a single share card directly from a string/hex/file payload, without running a Shamir
// split first. Useful to reprint one lost or damaged card for an already-existing share, given its
// known hex value, or to produce an ad-hoc card for arbitrary data.
public class CardCommand extends Command {
    private static final List<Prompts.Choice> TYPE_CHOICES = List.of(
            new Prompts.Choice("text", "Text", "Type the payload in directly"),
            new Prompts.Choice("hex", "Hex", "Provide the payload as a hex string"),
            new Prompts.Choice("file", "File", "Read the payload from a file on disk")
    );

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

        if (!isKnownType(type))
            return CommandResult.error("Unknown type");

        Optional<SecretSource> oSource = askSource(in, type);

        if (oSource.isEmpty())
            return CommandResult.error("No input");

        SplitScheme scheme;
        SecretSplitter splitter;

        try {
            scheme = new SplitScheme(oTotalShares.get(), oThreshold.get());
            splitter = SecretSplitter.reprint(oShareIndex.get(), scheme);
        } catch (IllegalArgumentException e) {
            return CommandResult.error(e.getMessage());
        }

        return create(new BackupRequest(oName.get(), null, splitter, oSource.get(), Main.getCurrentPath()),
                oShareIndex.get());
    }

    private static boolean isKnownType(String type) {
        return TYPE_CHOICES.stream().anyMatch(choice -> choice.value().equals(type));
    }

    private Optional<SecretSource> askSource(ArgSource in, String type) {
        return switch (type) {
            case "text" -> in.restOfLine("Enter text for the card:").map(SecretSource::ofText);
            case "hex" -> in.string("Enter hex payload:").map(hex -> SecretSource.ofHex(hex.trim()));
            case "file" -> in.existingFilePath("Path to file?").map(SecretSource::ofFile);
            default -> Optional.empty();
        };
    }

    private CommandResult create(BackupRequest request, int shareIndex) {
        BackupResult result;

        try {
            result = new BackupService().create(request);
        } catch (BackupException e) {
            return CommandResult.error(e.getMessage());
        }

        CommandResultBuilder builder = CommandResultBuilder.builder();

        builder.line("Card created: " + shareIndex + "/" + result.scheme().total() + ", "
                        + result.scheme().threshold() + " required to recover")
                .line(result.hasQr()
                        ? "QR error correction level: " + QrCodec.describeLevel(result.appliedLevel())
                        : "QR code: none (payload too large at every error correction level, hex block only)");

        if (!result.hasQr())
            builder.line("Warning: the payload did not produce a readable QR code at any error correction level; "
                    + "its bytes are fixed, so retrying would give the same result and the card was rendered "
                    + "with the hex block only.");

        for (var path : result.cardFiles())
            builder.line(path.getFileName().toString());

        for (var path : result.hexFiles())
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
