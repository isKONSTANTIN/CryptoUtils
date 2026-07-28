package su.knst.crypto.command.commands.qr;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import su.knst.crypto.Main;
import su.knst.crypto.TerminalWorker;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.FileUtils;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.TerminalQuestion;
import su.knst.crypto.utils.codes.AbstractCodeWorker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
        if (args.size() == 0)
            return runInteractive();

        return runScripted(args);
    }

    private CommandResult runScripted(ParamsContainer args) {
        Optional<String> oMode = args.stringV(0);

        if (oMode.isEmpty())
            return CommandResult.error("Mode not set");

        if (!(oMode.get().equals("scan") || oMode.get().equals("generate")))
            return CommandResult.error("Mode must be 'scan' or 'generate'");

        boolean mode = oMode.map((s) -> s.equals("scan")).get();

        if (mode) {
            Optional<String> oQRCodePath = args.stringV(1).map((p) -> Main.getCurrentPath().resolve(p).toString());

            if (oQRCodePath.isEmpty())
                return CommandResult.error("QR code path not set");

            Optional<Path> oResultPath = args.stringV(2).map((p) -> Main.getCurrentPath().resolve(p));

            return scan(oQRCodePath.get(), oResultPath.orElse(null));
        }else {
            int argIndex = 1;

            Optional<String> oQRCodePath = args.stringV(argIndex++).map((p) -> Main.getCurrentPath().resolve(p).toString());

            if (oQRCodePath.isEmpty())
                return CommandResult.error("QR code path not set");

            Optional<Integer> oPixels = args.intV(argIndex++);

            if (oPixels.isEmpty())
                return CommandResult.error("Pixels width not set");

            Optional<String> oData = args.stringV(argIndex++);

            if (oData.isEmpty())
                return CommandResult.error("Text or path not set");

            String startData = oData.get();

            Optional<ErrorCorrectionLevel> level = Arrays.stream(ErrorCorrectionLevel.values())
                    .filter((f) -> f.name().equals(startData.toUpperCase()))
                    .findFirst();

            if (level.isPresent()) {
                oData = args.stringV(argIndex++);

                if (oData.isEmpty())
                    return CommandResult.error("Text or path not set");
            }

            StringBuilder data = new StringBuilder(oData.get());

            try {
                if (data.toString().startsWith("f:")){
                    String path = Main.getCurrentPath().resolve(data.substring(2)).toString();

                    generateFromFile(oQRCodePath.get(), path, oPixels.get(), level.orElse(ErrorCorrectionLevel.L));
                }else {
                    for (int i = argIndex; i < args.size(); i++)
                        data.append(" ").append(args.stringV(i).orElseThrow());

                    generate(oQRCodePath.get(), data.toString(), oPixels.get(), level.orElse(ErrorCorrectionLevel.L));
                }
            } catch (Exception e) {
                e.printStackTrace();

                return CommandResult.error("Failed");
            }
        }

        return CommandResult.of("Done");
    }

    private CommandResult runInteractive() {
        TerminalWorker tw = Main.getTerminalWorker();

        Optional<String> oMode = Prompts.askChoice(tw, "Scan an existing code, or generate a new one?", MODE_CHOICES);

        if (oMode.isEmpty())
            return CommandResult.error("No input");

        if (oMode.get().equals("scan")) {
            Optional<Path> oImagePath = Prompts.askExistingFilePath(tw, "Path to the code image?");

            if (oImagePath.isEmpty())
                return CommandResult.error("No input");

            Optional<Path> oResultPath = Prompts.askNewFilePath(tw, "Save decoded content to file? (empty to print it instead)");

            return scan(oImagePath.get().toString(), oResultPath.orElse(null));
        } else {
            Optional<Path> oResultPath = Prompts.askNewFilePath(tw, "Result image path?");

            if (oResultPath.isEmpty())
                return CommandResult.error("No input");

            Optional<Integer> oPixels = Prompts.askInt(tw, "Image width/height in pixels?");

            if (oPixels.isEmpty())
                return CommandResult.error("No input");

            Optional<String> oLevel = Prompts.askChoice(tw, "Error correction level?", errorCorrectionChoices());

            if (oLevel.isEmpty())
                return CommandResult.error("No input");

            ErrorCorrectionLevel level = ErrorCorrectionLevel.valueOf(oLevel.get().toUpperCase());

            Optional<String> oSource = Prompts.askChoice(tw, "Encode text typed in directly, or the content of a file?", SOURCE_CHOICES);

            if (oSource.isEmpty())
                return CommandResult.error("No input");

            try {
                if (oSource.get().equals("file")) {
                    Optional<Path> oSourcePath = Prompts.askExistingFilePath(tw, "Path to file to encode?");

                    if (oSourcePath.isEmpty())
                        return CommandResult.error("No input");

                    generateFromFile(oResultPath.get().toString(), oSourcePath.get().toString(), oPixels.get(), level);
                } else {
                    Optional<String> oText = tw.ask(new TerminalQuestion("Text to encode?", null));

                    if (oText.isEmpty() || oText.get().isEmpty())
                        return CommandResult.error("No input");

                    generate(oResultPath.get().toString(), oText.get(), oPixels.get(), level);
                }
            } catch (Exception e) {
                e.printStackTrace();

                return CommandResult.error("Failed");
            }

            return CommandResult.of("Done");
        }
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
