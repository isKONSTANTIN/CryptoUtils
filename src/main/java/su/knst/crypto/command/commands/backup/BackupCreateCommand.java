package su.knst.crypto.command.commands.backup;

import com.codahale.shamir.Scheme;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.jline.builtins.Completers;
import su.knst.crypto.Main;
import su.knst.crypto.TerminalWorker;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.CommandResultBuilder;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.FileUtils;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.MnemonicUtils;
import su.knst.crypto.utils.TerminalQuestion;
import su.knst.crypto.utils.args.ArgsTreeBuilder;
import su.knst.crypto.utils.codes.LabeledQrImage;
import su.knst.crypto.utils.codes.SimpleQRCodeWorker;
import su.knst.crypto.utils.exceptions.WrongMnemonicException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class BackupCreateCommand extends Command {
    private static final List<String> TYPES = List.of("file", "text", "seed");
    private static final int QR_PIXEL_SIZE = 400;
    private static final ErrorCorrectionLevel ERROR_CORRECTION_LEVEL = ErrorCorrectionLevel.M;
    private static final int MAX_SPLIT_ATTEMPTS = 10;

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

        Optional<String> oName = args.stringV(1);

        if (oName.isEmpty())
            return CommandResult.error("Backup name not set");

        Optional<Integer> oAllParts = args.intV(2);

        if (oAllParts.isEmpty())
            return CommandResult.error("All parts not set");

        Optional<Integer> oForRecover = args.intV(3);

        if (oForRecover.isEmpty())
            return CommandResult.error("Parts for recover not set");

        byte[] secret;

        switch (type) {
            case "file" -> {
                Optional<String> oPath = args.stringV(4);

                if (oPath.isEmpty())
                    return CommandResult.error("Source path not set");

                try {
                    secret = Files.readAllBytes(Main.getCurrentPath().resolve(oPath.get()));
                } catch (IOException e) {
                    return CommandResult.error("Failed to read source file: " + e.getMessage());
                }
            }
            case "text" -> {
                if (args.size() <= 4)
                    return CommandResult.error("Source text not set");

                StringBuilder text = new StringBuilder();

                for (int i = 4; i < args.size(); i++)
                    text.append(i > 4 ? " " : "").append(args.stringV(i).orElseThrow());

                try {
                    secret = gzip(text.toString().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    return CommandResult.error("Failed to compress source text: " + e.getMessage());
                }
            }
            case "seed" -> {
                if (args.size() <= 4)
                    return CommandResult.error("Seed words not set");

                String[] words = new String[args.size() - 4];

                for (int i = 0; i < words.length; i++)
                    words[i] = args.stringV(i + 4).orElseThrow();

                try {
                    MnemonicUtils.checkMnemonic(words);
                } catch (WrongMnemonicException | NoSuchAlgorithmException e) {
                    return CommandResult.error("Failed to check mnemonic: " + e.getMessage());
                }

                secret = MnemonicUtils.entropyFromMnemonic(words);
            }
            default -> {
                return CommandResult.error("Type must be 'file', 'text' or 'seed'");
            }
        }

        return finish(type, oName.get(), oAllParts.get(), oForRecover.get(), secret);
    }

    private CommandResult runInteractive() {
        TerminalWorker tw = Main.getTerminalWorker();

        Optional<String> oType = tw.ask(new TerminalQuestion("Backup source type?", TYPES));

        if (oType.isEmpty())
            return CommandResult.error("No input");

        String type = oType.get();
        byte[] secret;

        switch (type) {
            case "file" -> {
                Optional<String> oPath = tw.ask(new TerminalQuestion("Path to file?", null));

                if (oPath.isEmpty())
                    return CommandResult.error("No input");

                Path path = Main.getCurrentPath().resolve(oPath.get());

                if (!path.toFile().isFile())
                    return CommandResult.error("File not found: " + path);

                try {
                    secret = Files.readAllBytes(path);
                } catch (IOException e) {
                    return CommandResult.error("Failed to read source file: " + e.getMessage());
                }
            }
            case "text" -> {
                Optional<String> oText = tw.ask(new TerminalQuestion("Enter text to backup:", null));

                if (oText.isEmpty() || oText.get().isEmpty())
                    return CommandResult.error("No input");

                try {
                    secret = gzip(oText.get().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    return CommandResult.error("Failed to compress source text: " + e.getMessage());
                }
            }
            case "seed" -> {
                Optional<String> oWords = tw.ask(new TerminalQuestion("Enter seed words separated by spaces:", null));

                if (oWords.isEmpty() || oWords.get().isBlank())
                    return CommandResult.error("No input");

                String[] words = oWords.get().trim().split("\\s+");

                try {
                    MnemonicUtils.checkMnemonic(words);
                } catch (WrongMnemonicException | NoSuchAlgorithmException e) {
                    return CommandResult.error("Failed to check mnemonic: " + e.getMessage());
                }

                secret = MnemonicUtils.entropyFromMnemonic(words);
            }
            default -> {
                return CommandResult.error("Unknown type");
            }
        }

        Optional<String> oName = tw.ask(new TerminalQuestion("Name for these backup copies?", null));

        if (oName.isEmpty() || oName.get().isBlank())
            return CommandResult.error("No input");

        Optional<Integer> oAllParts = askInt(tw, "Total number of parts (N)?");

        if (oAllParts.isEmpty())
            return CommandResult.error("No input");

        Optional<Integer> oForRecover = askInt(tw, "Parts required to recover (K)?");

        if (oForRecover.isEmpty())
            return CommandResult.error("No input");

        return finish(type, oName.get().trim(), oAllParts.get(), oForRecover.get(), secret);
    }

    private CommandResult finish(String type, String name, int allParts, int forRecover, byte[] secret) {
        if (secret.length == 0)
            return CommandResult.error("Source data is empty");

        String validationError = validateScheme(allParts, forRecover);

        if (validationError != null)
            return CommandResult.error(validationError);

        String typeLabel = type.toUpperCase();
        Map<Integer, BufferedImage> images = null;

        // Shamir split is randomized (a fresh polynomial every call), so on the rare chance a
        // particular share's QR code doesn't come back readable (barcode detection isn't
        // perfectly reliable for every possible bit pattern), redo the whole split with fresh
        // randomness and try again rather than ever handing back an unrestorable backup.
        for (int attempt = 0; attempt < MAX_SPLIT_ATTEMPTS && images == null; attempt++) {
            Scheme scheme = new Scheme(new SecureRandom(), allParts, forRecover);
            Map<Integer, byte[]> shares = scheme.split(secret);
            Map<Integer, BufferedImage> candidate = new LinkedHashMap<>();
            boolean allVerified = true;

            for (int i = 1; i <= allParts; i++) {
                String hex = HexUtils.bytesToHex(shares.get(i));
                String header = name + " " + i + "/" + allParts + " " + typeLabel;

                BufferedImage image;

                try {
                    image = LabeledQrImage.build(header, hex, QR_PIXEL_SIZE, ERROR_CORRECTION_LEVEL);
                } catch (WriterException e) {
                    // deterministic given the share length: retrying a resplit won't help
                    return CommandResult.error("Chunk " + i + " is too large to fit in a single QR code: " + e.getMessage());
                }

                if (!decodesTo(image, hex)) {
                    allVerified = false;
                    break;
                }

                candidate.put(i, image);
            }

            if (allVerified)
                images = candidate;
        }

        if (images == null)
            return CommandResult.error("Failed to produce reliably scannable QR codes for this backup, please try again");

        List<Path> written = new ArrayList<>();

        try {
            for (Map.Entry<Integer, BufferedImage> entry : images.entrySet()) {
                Path path = Main.getCurrentPath().resolve(name + "_" + entry.getKey() + ".png");

                FileUtils.createOwnerOnly(path);
                ImageIO.write(entry.getValue(), "png", path.toFile());

                written.add(path);
            }
        } catch (IOException e) {
            return CommandResult.error("Failed to write chunk files: " + e.getMessage());
        }

        CommandResultBuilder builder = CommandResultBuilder.builder();

        builder.line("Backup created: " + allParts + " parts, " + forRecover + " required to recover")
                .line("Type: " + typeLabel);

        for (Path path : written)
            builder.line(path.getFileName().toString());

        return builder.build();
    }

    private static boolean decodesTo(BufferedImage image, String expectedHex) {
        try {
            Path temp = Files.createTempFile("backup_qr_verify", ".png");

            try {
                ImageIO.write(image, "png", temp.toFile());

                return expectedHex.equals(new SimpleQRCodeWorker().readCode(temp.toString()));
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static String validateScheme(int allParts, int forRecover) {
        if (forRecover < 2)
            return "Parts for recover must be >= 2";

        if (allParts < forRecover)
            return "All parts must be >= parts for recover";

        if (allParts > 255)
            return "All parts must be <= 255";

        return null;
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

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }

        return baos.toByteArray();
    }

    @Override
    public String description() {
        return "Split a file, text or seed phrase into Shamir shares and print each as a labeled QR code";
    }

    @Override
    public String args() {
        return "file|text|seed <name> <all_parts> <parts_for_recover> <source...>";
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
