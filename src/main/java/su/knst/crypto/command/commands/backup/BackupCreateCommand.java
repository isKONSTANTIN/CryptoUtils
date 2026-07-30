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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class BackupCreateCommand extends Command {
    private static final List<Prompts.Choice> TYPE_CHOICES = List.of(
            new Prompts.Choice("file", "File", "Read the secret from a file on disk"),
            new Prompts.Choice("text", "Text", "Type the secret in directly"),
            new Prompts.Choice("seed", "Seed", "Split a BIP-39 mnemonic phrase")
    );

    @Override
    public CommandResult run(ParamsContainer args) {
        ArgSource in = args.size() == 0
                ? new InteractiveArgSource(Main.getTerminalWorker())
                : new ScriptedArgSource(args);

        return resolve(in);
    }

    // Argument order (type, name, tag_name, all_parts, for_recover, source...) matches args() and
    // is relied on by scripted callers, so it's kept identical for the interactive prompt sequence
    // too.
    private CommandResult resolve(ArgSource in) {
        Optional<String> oType = in.choice("Backup source type?", TYPE_CHOICES);

        if (oType.isEmpty())
            return CommandResult.error("No input");

        Optional<String> oName = in.string("Name for these backup copies?");

        if (oName.isEmpty())
            return CommandResult.error("No input");

        // Not a yes/no question: the answer itself decides whether tags are printed. Empty input
        // (or the scripted "null" placeholder) means "skip tags"; any other answer both opts in and
        // supplies the label printed on them, which is deliberately a separate piece of text from
        // the backup name above.
        Optional<String> oTagName = in.string("Name for the container tags? (empty to skip printing tags)");
        String tagName = (oTagName.isPresent() && !oTagName.get().equalsIgnoreCase("null")) ? oTagName.get() : null;

        Optional<Integer> oAllParts = in.integer("Total number of parts (N)?");

        if (oAllParts.isEmpty())
            return CommandResult.error("No input");

        Optional<Integer> oForRecover = in.integer("Parts required to recover (K)?");

        if (oForRecover.isEmpty())
            return CommandResult.error("No input");

        if (!isKnownType(oType.get()))
            return CommandResult.error("Unknown type");

        Optional<SecretSource> oSource = askSource(in, oType.get());

        if (oSource.isEmpty())
            return CommandResult.error("No input");

        SplitScheme scheme;

        try {
            scheme = SplitScheme.of(oAllParts.get(), oForRecover.get());
        } catch (IllegalArgumentException e) {
            return CommandResult.error(e.getMessage());
        }

        return create(new BackupRequest(oName.get(), tagName, SecretSplitter.shamir(scheme),
                oSource.get(), Main.getCurrentPath()));
    }

    private static boolean isKnownType(String type) {
        return TYPE_CHOICES.stream().anyMatch(choice -> choice.value().equals(type));
    }

    private Optional<SecretSource> askSource(ArgSource in, String type) {
        return switch (type) {
            case "file" -> in.existingFilePath("Path to file?").map(SecretSource::ofFile);
            case "text" -> in.restOfLine("Enter text to backup:").map(SecretSource::ofText);
            case "seed" -> in.words("Enter seed words separated by spaces:").map(SecretSource::ofSeed);
            default -> Optional.empty();
        };
    }

    private CommandResult create(BackupRequest request) {
        BackupResult result;

        try {
            result = new BackupService().create(request);
        } catch (BackupException e) {
            return CommandResult.error(e.getMessage());
        }

        return format(result);
    }

    private static CommandResult format(BackupResult result) {
        CommandResultBuilder builder = CommandResultBuilder.builder();

        builder.line("Backup created: " + result.scheme().total() + " parts, "
                        + result.scheme().threshold() + " required to recover")
                .line("Type: " + result.type().label())
                .line(result.hasQr()
                        ? "QR error correction level: " + QrCodec.describeLevel(result.appliedLevel())
                        : "QR code: none (share too large at every error correction level, hex block only)");

        appendFiles(builder, null, result.cardFiles());
        appendFiles(builder, "\nHex fallback:", result.hexFiles());
        appendFiles(builder, "\nTags:", result.tagFiles());
        appendFiles(builder, "\nPrint sheets:", result.sheetFiles());

        result.sheetFailure().ifPresent(
                reason -> builder.line("Warning: failed to generate print sheets: " + reason));

        return builder.build();
    }

    private static void appendFiles(CommandResultBuilder builder, String header, List<Path> files) {
        if (files.isEmpty())
            return;

        if (header != null)
            builder.line(header);

        for (Path path : files)
            builder.line(path.getFileName().toString());
    }

    @Override
    public String description() {
        return "Split a file, text or seed phrase into Shamir shares and print each as a labeled QR code";
    }

    @Override
    public String args() {
        return "file|text|seed <name> <tag_name|null> <all_parts> <parts_for_recover> <source...>";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.BACKUPS;
    }
}
