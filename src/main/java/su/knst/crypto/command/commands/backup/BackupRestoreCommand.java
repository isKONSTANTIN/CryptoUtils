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
import su.knst.crypto.command.commands.seed.SeedGeneratorCommand;
import su.knst.crypto.core.restore.RestoreException;
import su.knst.crypto.core.restore.RestoreMode;
import su.knst.crypto.core.restore.RestoreRequest;
import su.knst.crypto.core.restore.RestoreService;
import su.knst.crypto.core.restore.ShareInput;
import su.knst.crypto.core.secret.SecretSink;
import su.knst.crypto.utils.Prompts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BackupRestoreCommand extends Command {
    private static final List<Prompts.Choice> TYPE_CHOICES = List.of(
            new Prompts.Choice("file", "File", "Restore into a file on disk"),
            new Prompts.Choice("text", "Text", "Restore into plain text"),
            new Prompts.Choice("seed", "Seed", "Restore a BIP-39 mnemonic phrase")
    );

    @Override
    public CommandResult run(ParamsContainer args) {
        ArgSource in = args.size() == 0
                ? new InteractiveArgSource(Main.getTerminalWorker())
                : new ScriptedArgSource(args);

        return resolve(in);
    }

    private CommandResult resolve(ArgSource in) {
        Optional<String> oType = in.choice("Backup source type?", TYPE_CHOICES);

        if (oType.isEmpty())
            return CommandResult.error("No input");

        Optional<SecretSink> oSink = askSink(in, oType.get());

        if (oSink.isEmpty())
            return CommandResult.error(isKnownType(oType.get()) ? "No input" : "Unknown type");

        Optional<List<ShareInput>> oChunks = askChunks(in);

        if (oChunks.isEmpty())
            return CommandResult.error("No input");

        return restore(new RestoreRequest(oSink.get(), RestoreMode.SHAMIR, oChunks.get()));
    }

    private static boolean isKnownType(String type) {
        return TYPE_CHOICES.stream().anyMatch(choice -> choice.value().equals(type));
    }

    private Optional<SecretSink> askSink(ArgSource in, String type) {
        return switch (type) {
            case "file" -> in.newFilePath("Output path for the restored file?").map(SecretSink::toFile);
            case "text" -> Optional.of(SecretSink.toText());
            case "seed" -> Optional.of(SecretSink.toSeed());
            default -> Optional.empty();
        };
    }

    // Scripted mode has no separate "total" argument - the chunk list is however many positional
    // tokens are left. Interactive mode has to ask for a count up front instead, since there's no
    // natural end-of-input signal on a single prompt loop.
    private Optional<List<ShareInput>> askChunks(ArgSource in) {
        List<ShareInput> chunks = new ArrayList<>();

        if (in.interactive()) {
            Optional<Integer> oTotal = in.integer("How many chunks were there in total?");

            if (oTotal.isEmpty())
                return Optional.empty();

            for (int i = 1; i <= oTotal.get(); i++) {
                Optional<String> oToken =
                        in.stringWithFileCompletion("Chunk #" + i + ": file path, hex string, or empty to skip:");

                chunks.add(oToken.map(BackupRestoreCommand::toShareInput).orElseGet(ShareInput.Skipped::new));
            }
        } else {
            Optional<String> oToken;

            while ((oToken = in.string(null)).isPresent())
                chunks.add(toShareInput(oToken.get()));
        }

        return Optional.of(chunks);
    }

    /** A token naming an existing file is a card to scan; anything else is hex read off one by eye. */
    private static ShareInput toShareInput(String token) {
        if (token.equalsIgnoreCase("null"))
            return new ShareInput.Skipped();

        Path path = Main.getCurrentPath().resolve(token);

        return path.toFile().isFile() ? new ShareInput.FromFile(path) : new ShareInput.FromHex(token);
    }

    private CommandResult restore(RestoreRequest request) {
        SecretSink.Written written;

        try {
            written = new RestoreService().restore(request);
        } catch (RestoreException e) {
            return CommandResult.error(e.getMessage());
        }

        if (written.file() != null)
            return CommandResult.of("Restored file written to " + written.file());

        if (written.words() != null)
            return CommandResultBuilder.builder()
                    .line("Recovered seed:")
                    .line(SeedGeneratorCommand.formatMnemonic(written.words()))
                    .build();

        return CommandResult.of(written.text());
    }

    @Override
    public String description() {
        return "Reconstruct a file, text or seed phrase from Shamir shares recovered from QR codes or manually entered hex";
    }

    @Override
    public String args() {
        return "file <output_path> <chunk_1|null> ... | text <chunk_1|null> ... | seed <chunk_1|null> ...";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.BACKUPS;
    }
}
