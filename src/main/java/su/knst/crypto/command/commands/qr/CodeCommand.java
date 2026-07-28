package su.knst.crypto.command.commands.qr;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import su.knst.crypto.Main;
import su.knst.crypto.command.ArgSource;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.InteractiveArgSource;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.ScriptedArgSource;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.FileUtils;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.codes.AbstractCodeWorker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CodeCommand extends Command {

    private static final List<Prompts.Choice> MODE_CHOICES = List.of(
            new Prompts.Choice("scan", "Scan", "Read a code image and print/save its decoded content"),
            new Prompts.Choice("generate", "Generate", "Encode text or a file's content into a code image")
    );

    private static final List<Prompts.Choice> SOURCE_CHOICES = List.of(
            new Prompts.Choice("text", "Text", "Type the content to encode directly"),
            new Prompts.Choice("file", "File", "Encode the raw bytes of a file (base64-wrapped)")
    );

    // ISO 18004 QR error-correction levels: bit patterns come from zxing's ErrorCorrectionLevel
    // enum, the recoverable-damage percentages are the standard's own documented figures.
    private static final Map<String, String> LEVEL_LABELS = Map.of(
            "l", "Low",
            "m", "Medium",
            "q", "Quartile",
            "h", "High"
    );
    private static final Map<String, String> LEVEL_DESCRIPTIONS = Map.of(
            "l", "~7% of the code can be damaged and still scan",
            "m", "~15% of the code can be damaged and still scan",
            "q", "~25% of the code can be damaged and still scan",
            "h", "~30% of the code can be damaged and still scan"
    );

    protected final AbstractCodeWorker worker;
    public CodeCommand(AbstractCodeWorker worker) {
        this.worker = worker;
    }

    @Override
    public CommandResult run(ParamsContainer args) {
        ArgSource in = args.size() == 0
                ? new InteractiveArgSource(Main.getTerminalWorker())
                : new ScriptedArgSource(args);

        return resolve(in);
    }

    private CommandResult resolve(ArgSource in) {
        Optional<String> oMode = in.choice("Scan an existing code, or generate a new one?", MODE_CHOICES);

        if (oMode.isEmpty())
            return CommandResult.error("No input");

        if (oMode.get().equals("scan")) {
            Optional<Path> oImagePath = in.existingFilePath("Path to the code image?");

            if (oImagePath.isEmpty())
                return CommandResult.error("No input");

            Optional<Path> oResultPath = in.newFilePath("Save decoded content to file? (empty to print it instead)");

            return scan(oImagePath.get().toString(), oResultPath.orElse(null));
        }

        Optional<Path> oResultPath = in.newFilePath("Result image path?");

        if (oResultPath.isEmpty())
            return CommandResult.error("No input");

        Optional<Integer> oPixels = in.integer("Image width/height in pixels?");

        if (oPixels.isEmpty())
            return CommandResult.error("No input");

        // scripted mode may omit the level token entirely, defaulting to the lowest one
        Optional<String> oLevel = in.choiceOr("Error correction level?", errorCorrectionChoices(), "l");

        if (oLevel.isEmpty())
            return CommandResult.error("No input");

        ErrorCorrectionLevel level = ErrorCorrectionLevel.valueOf(oLevel.get().toUpperCase());

        try {
            if (in.interactive()) {
                Optional<String> oSource = in.choice("Encode text typed in directly, or the content of a file?", SOURCE_CHOICES);

                if (oSource.isEmpty())
                    return CommandResult.error("No input");

                if (oSource.get().equals("file")) {
                    Optional<Path> oSourcePath = in.existingFilePath("Path to file to encode?");

                    if (oSourcePath.isEmpty())
                        return CommandResult.error("No input");

                    generateFromFile(oResultPath.get().toString(), oSourcePath.get().toString(), oPixels.get(), level);
                } else {
                    Optional<String> oText = in.string("Text to encode?");

                    if (oText.isEmpty())
                        return CommandResult.error("No input");

                    generate(oResultPath.get().toString(), oText.get(), oPixels.get(), level);
                }
            } else {
                // "f:path" encodes a file's content, anything else is encoded as literal text
                Optional<String> oData = in.restOfLine("Text to encode, or f:path to encode a file's content?");

                if (oData.isEmpty())
                    return CommandResult.error("No input");

                String data = oData.get();

                if (data.startsWith("f:")) {
                    String path = Main.getCurrentPath().resolve(data.substring(2)).toString();

                    generateFromFile(oResultPath.get().toString(), path, oPixels.get(), level);
                } else {
                    generate(oResultPath.get().toString(), data, oPixels.get(), level);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();

            return CommandResult.error("Failed");
        }

        return CommandResult.of("Done");
    }

    private static List<Prompts.Choice> errorCorrectionChoices() {
        List<Prompts.Choice> choices = new ArrayList<>();

        for (ErrorCorrectionLevel level : ErrorCorrectionLevel.values()) {
            String value = level.name().toLowerCase();

            choices.add(new Prompts.Choice(
                    value,
                    LEVEL_LABELS.getOrDefault(value, level.name()),
                    LEVEL_DESCRIPTIONS.get(value)
            ));
        }

        return choices;
    }

    private CommandResult scan(String qrCodePath, Path resultPath) {
        String result;
        try {
            result = worker.readCode(qrCodePath);
            Objects.requireNonNull(result);

        } catch (Exception e) {
            e.printStackTrace();

            return CommandResult.error("Fail to scan");
        }

        if (resultPath != null) {
            try {
                FileUtils.writeOwnerOnly(resultPath, Base64.getDecoder().decode(result));
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.error("Fail to write result: " + result);
            }

            return CommandResult.of("Done");
        }

        return CommandResult.of(result);
    }

    public void generate(String resultPath, String text, int pixelsSide, ErrorCorrectionLevel level) throws IOException, WriterException {
        // create the file with owner-only permissions before the QR image is written into it
        FileUtils.createOwnerOnly(Path.of(resultPath));
        worker.generateCode(text, resultPath, pixelsSide, pixelsSide, level);
    }

    public void generate(String resultPath, String text, int pixelsSide) throws IOException, WriterException {
        generate(resultPath, text, pixelsSide, ErrorCorrectionLevel.L);
    }

    public void generateFromFile(String resultPath, String sourcePath, int pixelsSide) throws IOException, WriterException {
        generateFromFile(resultPath, sourcePath, pixelsSide, ErrorCorrectionLevel.L);
    }

    public void generateFromFile(String resultPath, String sourcePath, int pixelsSide, ErrorCorrectionLevel level) throws IOException, WriterException {
        generate(resultPath, Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(sourcePath))), pixelsSide, level);
    }

    @Override
    public String description() {
        return "Scan a QR/PDF417 code image to recover its content, or generate a new code image from text or a file";
    }

    @Override
    public String args() {
        return "scan <image path> [result path] | generate <result path> <pixels width> [error correction level] <source text/f:path>";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.BACKUPS;
    }
}
