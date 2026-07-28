package su.knst.crypto.command.commands.backup;

import com.codahale.shamir.Scheme;
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
import su.knst.crypto.utils.FileUtils;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.MnemonicUtils;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.codes.SimpleQRCodeWorker;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

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

        String type = oType.get();
        String outputPath = null;

        if (type.equals("file")) {
            Optional<Path> oOutput = in.newFilePath("Output path for the restored file?");

            if (oOutput.isEmpty())
                return CommandResult.error("No input");

            outputPath = oOutput.get().toString();
        }

        Map<Integer, byte[]> parts = new HashMap<>();

        // Scripted mode has no separate "total" argument - the chunk list is however many
        // positional tokens are left. Interactive mode has to ask for a count up front instead,
        // since there's no natural end-of-input signal on a single prompt loop.
        if (in.interactive()) {
            Optional<Integer> oTotal = in.integer("How many chunks were there in total?");

            if (oTotal.isEmpty())
                return CommandResult.error("No input");

            for (int i = 1; i <= oTotal.get(); i++) {
                Optional<String> oToken = in.stringWithFileCompletion("Chunk #" + i + ": file path, hex string, or empty to skip:");

                if (oToken.isEmpty())
                    continue;

                CommandResult error = resolveChunk(i, oToken.get(), parts);

                if (error != null)
                    return error;
            }
        } else {
            int chunkIndex = 0;
            Optional<String> oToken;

            while ((oToken = in.string(null)).isPresent()) {
                chunkIndex++;
                String token = oToken.get();

                if (token.equalsIgnoreCase("null"))
                    continue;

                CommandResult error = resolveChunk(chunkIndex, token, parts);

                if (error != null)
                    return error;
            }
        }

        if (parts.isEmpty())
            return CommandResult.error("No chunks provided");

        return finish(type, outputPath, parts);
    }

    // resolves one chunk token (file path or raw hex) into `parts`, returning a
    // CommandResult.error(...) if it fails, or null on success
    private static CommandResult resolveChunk(int chunkIndex, String token, Map<Integer, byte[]> parts) {
        String hex;

        Path path = Main.getCurrentPath().resolve(token);

        if (path.toFile().isFile()) {
            try {
                hex = new SimpleQRCodeWorker().readCode(path.toString());
            } catch (Exception e) {
                return CommandResult.error("Chunk " + chunkIndex + ": failed to read QR code: " + e.getMessage());
            }

            if (hex == null)
                return CommandResult.error("Chunk " + chunkIndex + ": QR code not found in image");
        } else {
            hex = token;
        }

        if (!HexUtils.isValidHex(hex))
            return CommandResult.error("Chunk " + chunkIndex + ": invalid hex string");

        parts.put(chunkIndex, HexUtils.hexStringToByteArray(hex));

        return null;
    }

    private CommandResult finish(String type, String outputPath, Map<Integer, byte[]> parts) {
        byte[] secret;

        try {
            // the scheme's own n/k are only used by its constructor's validation (k > 1, n >= k) -
            // join() reconstructs purely from the given parts map, so these values don't need to
            // match the original split's n/k, just be valid
            Scheme scheme = new Scheme(new SecureRandom(), Math.max(2, parts.size()), 2);
            secret = scheme.join(parts);
        } catch (IllegalArgumentException e) {
            return CommandResult.error("Failed to reconstruct secret: " + e.getMessage());
        }

        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(secret))) {
            secret = gzip.readAllBytes();
        } catch (IOException e) {
            return CommandResult.error("Failed to decompress recovered data: " + e.getMessage());
        }

        switch (type) {
            case "file" -> {
                try {
                    FileUtils.writeOwnerOnly(Main.getCurrentPath().resolve(outputPath), secret);
                } catch (IOException e) {
                    return CommandResult.error("Failed to write output file: " + e.getMessage());
                }

                return CommandResult.of("Restored file written to " + outputPath);
            }
            case "text" -> {
                return CommandResult.of(new String(secret, StandardCharsets.UTF_8));
            }
            case "seed" -> {
                String[] words;

                try {
                    words = MnemonicUtils.createMnemonic(secret);
                } catch (NoSuchAlgorithmException | RuntimeException e) {
                    return CommandResult.error("Failed to build mnemonic: " + e.getMessage());
                }

                return CommandResultBuilder.builder()
                        .line("Recovered seed:")
                        .line(SeedGeneratorCommand.formatMnemonic(words))
                        .build();
            }
            default -> {
                return CommandResult.error("Unknown type");
            }
        }
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
