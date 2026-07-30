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
            new Prompts.Choice("seed", "Seed", "Back up a BIP-39 mnemonic phrase"),
            new Prompts.Choice("hex", "Hex", "Reprint a card for a share you already hold")
    );

    private static final List<Prompts.Choice> SPLIT_CHOICES = List.of(
            new Prompts.Choice("split", "Shamir split", "N cards, any K of which restore the secret"),
            new Prompts.Choice("single", "Single card", "One card holding the whole secret, no split")
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

        if (!isKnownType(oType.get()))
            return CommandResult.error("Unknown type");

        Optional<SecretSplitter> oSplitter;

        try {
            oSplitter = askSplitter(in, oType.get());
        } catch (IllegalArgumentException e) {
            return CommandResult.error(e.getMessage());
        }

        if (oSplitter.isEmpty())
            return CommandResult.error("No input");

        Optional<SecretSource> oSource = askSource(in, oType.get());

        if (oSource.isEmpty())
            return CommandResult.error("No input");

        return create(new BackupRequest(oName.get(), tagName, oSplitter.get(),
                oSource.get(), Main.getCurrentPath()));
    }

    /**
     * Which splitter to use is the only thing that differs between a Shamir backup, a whole-secret
     * card and a reprint - everything downstream is the same pipeline.
     */
    private Optional<SecretSplitter> askSplitter(ArgSource in, String type) {
        // a hex payload is an existing share by definition, so it is always a reprint: asking
        // whether to split it further would produce a card that no longer matches its siblings
        if (type.equals("hex"))
            return askReprintSplitter(in);

        Optional<String> oMode = in.choice("Split the secret into parts?", SPLIT_CHOICES);

        if (oMode.isEmpty())
            return Optional.empty();

        if (oMode.get().equals("single"))
            return Optional.of(SecretSplitter.single());

        Optional<Integer> oAllParts = in.integer("Total number of parts (N)?");

        if (oAllParts.isEmpty())
            return Optional.empty();

        Optional<Integer> oForRecover = in.integer("Parts required to recover (K)?");

        if (oForRecover.isEmpty())
            return Optional.empty();

        return Optional.of(SecretSplitter.shamir(SplitScheme.of(oAllParts.get(), oForRecover.get())));
    }

    private Optional<SecretSplitter> askReprintSplitter(ArgSource in) {
        Optional<Integer> oShareIndex = in.integer("Which share is this card for?");

        if (oShareIndex.isEmpty())
            return Optional.empty();

        Optional<Integer> oAllParts = in.integer("Total number of parts (N)?");

        if (oAllParts.isEmpty())
            return Optional.empty();

        Optional<Integer> oForRecover = in.integer("Parts required to recover (K)?");

        if (oForRecover.isEmpty())
            return Optional.empty();

        return Optional.of(SecretSplitter.reprint(
                oShareIndex.get(), new SplitScheme(oAllParts.get(), oForRecover.get())));
    }

    private static boolean isKnownType(String type) {
        return TYPE_CHOICES.stream().anyMatch(choice -> choice.value().equals(type));
    }

    private Optional<SecretSource> askSource(ArgSource in, String type) {
        return switch (type) {
            case "file" -> in.existingFilePath("Path to file?").map(SecretSource::ofFile);
            case "text" -> in.restOfLine("Enter text to backup:").map(SecretSource::ofText);
            case "seed" -> in.words("Enter seed words separated by spaces:").map(SecretSource::ofSeed);
            case "hex" -> in.string("Enter the share's hex payload:").map(hex -> SecretSource.ofHex(hex.trim()));
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

        builder.line(result.scheme().isSplit()
                        ? "Backup created: " + result.scheme().total() + " parts, "
                                + result.scheme().threshold() + " required to recover"
                        : "Backup created: a single card holding the whole secret")
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
        return "Print a file, text or seed phrase onto scannable backup cards - split into Shamir "
                + "shares, whole on a single card, or reprinting a card for a share you already hold";
    }

    @Override
    public String args() {
        return "file|text|seed|hex <name> <tag_name|null> <split|single> [<all_parts> <parts_for_recover>] <source...>";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.BACKUPS;
    }
}
