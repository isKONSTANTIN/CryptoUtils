package su.knst.crypto.command.commands.backup;

import com.codahale.shamir.Scheme;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
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
import su.knst.crypto.utils.MnemonicUtils;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.codes.ShareCardImage;
import su.knst.crypto.utils.codes.SharePrintLayoutPlanner;
import su.knst.crypto.utils.codes.SharePrintPageRenderer;
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
import java.time.LocalDate;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class BackupCreateCommand extends Command {
    private static final List<Prompts.Choice> TYPE_CHOICES = List.of(
            new Prompts.Choice("file", "File", "Read the secret from a file on disk"),
            new Prompts.Choice("text", "Text", "Type the secret in directly"),
            new Prompts.Choice("seed", "Seed", "Split a BIP-39 mnemonic phrase")
    );
    // Tried in order, most error-correction first; if a share is too large to fit a QR code at
    // one level, we drop down a notch rather than giving up on the QR entirely.
    private static final ErrorCorrectionLevel[] ERROR_CORRECTION_LEVELS = {
            ErrorCorrectionLevel.H, ErrorCorrectionLevel.Q, ErrorCorrectionLevel.M, ErrorCorrectionLevel.L
    };
    private static final int MAX_SPLIT_ATTEMPTS = 10;
    private static final int PNG_DPI = 300;

    @Override
    public CommandResult run(ParamsContainer args) {
        ArgSource in = args.size() == 0
                ? new InteractiveArgSource(Main.getTerminalWorker())
                : new ScriptedArgSource(args);

        return resolve(in);
    }

    // Argument order (type, name, all_parts, for_recover, source...) matches args() and is relied
    // on by scripted callers, so it's kept identical for the interactive prompt sequence too.
    private CommandResult resolve(ArgSource in) {
        Optional<String> oType = in.choice("Backup source type?", TYPE_CHOICES);

        if (oType.isEmpty())
            return CommandResult.error("No input");

        String type = oType.get();

        Optional<String> oName = in.string("Name for these backup copies?");

        if (oName.isEmpty())
            return CommandResult.error("No input");

        Optional<Integer> oAllParts = in.integer("Total number of parts (N)?");

        if (oAllParts.isEmpty())
            return CommandResult.error("No input");

        Optional<Integer> oForRecover = in.integer("Parts required to recover (K)?");

        if (oForRecover.isEmpty())
            return CommandResult.error("No input");

        byte[] secret;

        switch (type) {
            case "file" -> {
                Optional<Path> oPath = in.existingFilePath("Path to file?");

                if (oPath.isEmpty())
                    return CommandResult.error("No input");

                try {
                    secret = Files.readAllBytes(oPath.get());
                } catch (IOException e) {
                    return CommandResult.error("Failed to read source file: " + e.getMessage());
                }
            }
            case "text" -> {
                Optional<String> oText = in.restOfLine("Enter text to backup:");

                if (oText.isEmpty())
                    return CommandResult.error("No input");

                secret = oText.get().getBytes(StandardCharsets.UTF_8);
            }
            case "seed" -> {
                Optional<String[]> oWords = in.words("Enter seed words separated by spaces:");

                if (oWords.isEmpty())
                    return CommandResult.error("No input");

                String[] words = oWords.get();

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

        return finish(type, oName.get(), oAllParts.get(), oForRecover.get(), secret);
    }

    private CommandResult finish(String type, String name, int allParts, int forRecover, byte[] secret) {
        if (secret.length == 0)
            return CommandResult.error("Source data is empty");

        try {
            secret = gzip(secret);
        } catch (IOException e) {
            return CommandResult.error("Failed to compress source data: " + e.getMessage());
        }

        String validationError = validateScheme(allParts, forRecover);

        if (validationError != null)
            return CommandResult.error(validationError);

        String typeLabel = type.toUpperCase();
        LocalDate createdOn = LocalDate.now();
        Map<Integer, BufferedImage> images = null;
        ErrorCorrectionLevel appliedLevel = null;
        boolean appliedHasQr = true;

        // Shamir split is randomized (a fresh polynomial every call), so on the rare chance a
        // particular share's QR code doesn't come back readable (barcode detection isn't
        // perfectly reliable for every possible bit pattern), redo the whole split with fresh
        // randomness and try again rather than ever handing back an unrestorable backup.
        for (int attempt = 0; attempt < MAX_SPLIT_ATTEMPTS && images == null; attempt++) {
            Scheme scheme = new Scheme(new SecureRandom(), allParts, forRecover);
            Map<Integer, byte[]> shares = scheme.split(secret);

            Map<Integer, String> hexByPart = new LinkedHashMap<>();
            Map<Integer, String> checksumByPart = new LinkedHashMap<>();

            for (int i = 1; i <= allParts; i++) {
                byte[] shareBytes = shares.get(i);
                hexByPart.put(i, HexUtils.bytesToHex(shareBytes));

                try {
                    checksumByPart.put(i, HexUtils.hexHash(shareBytes).substring(0, 6).toLowerCase(Locale.ROOT));
                } catch (NoSuchAlgorithmException e) {
                    return CommandResult.error("Failed to compute checksum: " + e.getMessage());
                }
            }

            // Start at the most error-correcting level and step down one notch at a time whenever
            // any share is too large to fit a QR code at the current level.
            Map<Integer, BufferedImage> candidate = null;
            ErrorCorrectionLevel fittingLevel = null;
            boolean decodeFailed = false;

            for (ErrorCorrectionLevel level : ERROR_CORRECTION_LEVELS) {
                candidate = new LinkedHashMap<>();
                boolean levelFits = true;

                for (int i = 1; i <= allParts; i++) {
                    BufferedImage image;

                    try {
                        image = ShareCardImage.build(new ShareCardImage.ShareCardData(
                                name, i, allParts, forRecover, createdOn, hexByPart.get(i), checksumByPart.get(i),
                                typeLabel, level, true));
                    } catch (WriterException e) {
                        levelFits = false;
                        break;
                    }

                    // a share that renders but doesn't decode back to its own hex isn't safe to
                    // hand out: this is the flaky case the outer attempt loop exists for, so a
                    // fresh split (not just a lower error-correction level) is the real fix
                    if (!decodesTo(image, hexByPart.get(i))) {
                        levelFits = false;
                        decodeFailed = true;
                        break;
                    }

                    candidate.put(i, image);
                }

                if (levelFits) {
                    fittingLevel = level;
                    break;
                }

                candidate = null;
            }

            if (candidate == null && decodeFailed)
                continue;

            if (candidate == null) {
                // no error-correction level was low enough: fall back to QR-less cards (the hex
                // block is still the authoritative fallback path) rather than failing the backup
                candidate = new LinkedHashMap<>();
                ErrorCorrectionLevel lowestLevel = ERROR_CORRECTION_LEVELS[ERROR_CORRECTION_LEVELS.length - 1];

                for (int i = 1; i <= allParts; i++) {
                    try {
                        candidate.put(i, ShareCardImage.build(new ShareCardImage.ShareCardData(
                                name, i, allParts, forRecover, createdOn, hexByPart.get(i), checksumByPart.get(i),
                                typeLabel, lowestLevel, false)));
                    } catch (WriterException e2) {
                        return CommandResult.error("Chunk " + i + " failed to render: " + e2.getMessage());
                    }
                }

                fittingLevel = lowestLevel;
                appliedHasQr = false;
            }

            images = candidate;
            appliedLevel = fittingLevel;
        }

        if (images == null)
            return CommandResult.error("Failed to produce reliably readable QR codes after " + MAX_SPLIT_ATTEMPTS + " attempts");

        List<Path> written = new ArrayList<>();

        try {
            for (Map.Entry<Integer, BufferedImage> entry : images.entrySet()) {
                Path path = Main.getCurrentPath().resolve(name + "_" + entry.getKey() + ".png");

                FileUtils.createOwnerOnly(path);
                ShareCardImage.writePng(entry.getValue(), path, PNG_DPI);

                written.add(path);
            }
        } catch (IOException e) {
            return CommandResult.error("Failed to write chunk files: " + e.getMessage());
        }

        List<BufferedImage> orderedImages = new ArrayList<>(images.values());

        List<SharePrintLayoutPlanner.CardInput> cardInputs = new ArrayList<>();

        for (BufferedImage image : orderedImages)
            cardInputs.add(new SharePrintLayoutPlanner.CardInput(image.getWidth(), image.getHeight()));

        List<Path> writtenPrintPages = new ArrayList<>();
        String printFailure = null;

        try {
            int cardWidthPx = orderedImages.get(0).getWidth();

            SharePrintLayoutPlanner.PrintPlan printPlan = SharePrintLayoutPlanner.plan(
                    cardInputs, SharePrintLayoutPlanner.PageConfig.a4FittingCardWidth(cardWidthPx));

            List<BufferedImage> printPages = SharePrintPageRenderer.render(printPlan, orderedImages);

            for (int i = 0; i < printPages.size(); i++) {
                Path path = Main.getCurrentPath().resolve(name + "_print_" + (i + 1) + ".png");

                FileUtils.createOwnerOnly(path);
                ShareCardImage.writePng(printPages.get(i), path, PNG_DPI);

                writtenPrintPages.add(path);
            }
        } catch (SharePrintLayoutPlanner.CardTooLargeException | IOException e) {
            // per-share PNGs are already written and are the authoritative backup; print sheets
            // are a convenience feature only and must never fail the whole command. A layout
            // failure (one share too large to print) rolls back to no print sheets at all rather
            // than a partial set.
            writtenPrintPages = List.of();
            printFailure = e.getMessage();
        }

        CommandResultBuilder builder = CommandResultBuilder.builder();

        builder.line("Backup created: " + allParts + " parts, " + forRecover + " required to recover")
                .line("Type: " + typeLabel)
                .line(appliedHasQr
                        ? "QR error correction level: " + describeLevel(appliedLevel)
                        : "QR code: none (share too large at every error correction level, hex block only)");

        for (Path path : written)
            builder.line(path.getFileName().toString());

        if (!writtenPrintPages.isEmpty()) {
            builder.line("\nPrint sheets:");

            for (Path path : writtenPrintPages)
                builder.line(path.getFileName().toString());
        }

        if (printFailure != null)
            builder.line("Warning: failed to generate print sheets: " + printFailure);

        return builder.build();
    }

    private static String describeLevel(ErrorCorrectionLevel level) {
        String name = switch (level) {
            case L -> "Low";
            case M -> "Medium";
            case Q -> "Quartile";
            case H -> "High";
        };

        int position = Arrays.asList(ERROR_CORRECTION_LEVELS).indexOf(level) + 1;

        return name + " (" + position + "/" + ERROR_CORRECTION_LEVELS.length + ")";
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
}
