package su.knst.crypto.command.commands.backup;

import com.codahale.shamir.Scheme;
import org.jline.builtins.Completers;
import su.knst.crypto.Main;
import su.knst.crypto.TerminalWorker;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.CommandResultBuilder;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.command.commands.seed.SeedGeneratorCommand;
import su.knst.crypto.utils.FileUtils;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.MnemonicUtils;
import su.knst.crypto.utils.TerminalQuestion;
import su.knst.crypto.utils.args.ArgsTreeBuilder;
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
    private static final List<String> TYPES = List.of("file", "text", "seed");

    @Override
    public CommandResult run(ParamsContainer args) {
        if (args.size() == 0)
            return runInteractive();

        return runScripted(args);
    }

    private CommandResult runScripted(ParamsContainer args) {
        Optional<String> oType = args.stringV(0);

        if (oType.isEmpty() || !TYPES.contains(oType.get()))
            return CommandResult.error("Type must be 'file', 'text' or 'seed'");

        String type = oType.get();
        String outputPath = null;
        int chunkStart = 1;

        if (type.equals("file")) {
            Optional<String> oOutput = args.stringV(1);

            if (oOutput.isEmpty())
                return CommandResult.error("Output path not set");

            outputPath = oOutput.get();
            chunkStart = 2;
        }

        if (args.size() <= chunkStart)
            return CommandResult.error("No chunks provided");

        Map<Integer, byte[]> parts = new HashMap<>();

        for (int i = chunkStart; i < args.size(); i++) {
            int chunkIndex = i - chunkStart + 1;
            String token = args.stringV(i).orElseThrow();

            if (token.equalsIgnoreCase("null"))
                continue;

            CommandResult error = resolveChunk(chunkIndex, token, parts);

            if (error != null)
                return error;
        }

        if (parts.isEmpty())
            return CommandResult.error("No chunks provided");

        return finish(type, outputPath, parts);
    }

    private CommandResult runInteractive() {
        TerminalWorker tw = Main.getTerminalWorker();

        Optional<String> oType = tw.ask(new TerminalQuestion("Backup source type?", TYPES));

        if (oType.isEmpty())
            return CommandResult.error("No input");

        String type = oType.get();
        String outputPath = null;

        if (type.equals("file")) {
            Optional<String> oOutput = tw.ask(new TerminalQuestion("Output path for the restored file?", null));

            if (oOutput.isEmpty() || oOutput.get().isBlank())
                return CommandResult.error("No input");

            outputPath = oOutput.get().trim();
        }

        Optional<Integer> oTotal = askInt(tw, "How many chunks were there in total?");

        if (oTotal.isEmpty())
            return CommandResult.error("No input");

        Map<Integer, byte[]> parts = new HashMap<>();

        for (int i = 1; i <= oTotal.get(); i++) {
            Optional<String> oToken = tw.ask(new TerminalQuestion(
                    "Chunk #" + i + ": file path, hex string, or empty to skip:", null));

            if (oToken.isEmpty() || oToken.get().isBlank())
                continue;

            CommandResult error = resolveChunk(i, oToken.get().trim(), parts);

            if (error != null)
                return error;
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
                String text;

                try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(secret))) {
                    text = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    if (!isBinary(secret)) {
                        return CommandResultBuilder.builder()
                                .line("Warning: recovered data is not gzip-compressed, showing raw text instead")
                                .line(new String(secret, StandardCharsets.UTF_8))
                                .build();
                    }

                    return CommandResult.error("Failed to decompress recovered text: " + e.getMessage());
                }

                return CommandResult.of(text);
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

    // heuristic: valid, strict UTF-8 with no control characters other than common whitespace
    private static boolean isBinary(byte[] data) {
        String decoded;

        try {
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(data))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return true;
        }

        for (int i = 0; i < decoded.length(); i++) {
            char c = decoded.charAt(i);

            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')
                return true;
        }

        return false;
    }

    private static Optional<Integer> askInt(TerminalWorker tw, String question) {
        while (true) {
            Optional<String> answer = tw.ask(new TerminalQuestion(question, null));

            if (answer.isEmpty())
                return Optional.empty();

            try {
                return Optional.of(Integer.parseInt(answer.get().trim()));
            } catch (NumberFormatException ignored) {
                // ask again
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

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .subTree().addPossibleArgs("file", "text", "seed")

                .recursiveSubTree()
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .parent()

                .parent()
                .build();
    }
}
