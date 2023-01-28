package su.knrg.crypto.command.commands.qr;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.jline.builtins.Completers;
import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.command.commands.CommandTag;
import su.knrg.crypto.utils.SimpleFileWorker;
import su.knrg.crypto.utils.args.ArgsTreeBuilder;
import su.knrg.crypto.utils.codes.AbstractCodeWorker;
import su.knrg.crypto.utils.codes.SimpleQRCodeWorker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

public class CodeCommand extends Command {

    protected AbstractCodeWorker worker;
    public CodeCommand(AbstractCodeWorker worker) {
        this.worker = worker;
    }

    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> oMode = args.stringV(0);

        if (oMode.isEmpty())
            return CommandResult.of("Mode not set", true);

        if (!(oMode.get().equals("scan") || oMode.get().equals("generate")))
            return CommandResult.of("Mode must be 'scan' or 'generate'", true);

        boolean mode = oMode.map((s) -> s.equals("scan")).get();

        if (mode) {
            Optional<String> oQRCodePath = args.stringV(1);

            if (oQRCodePath.isEmpty())
                return CommandResult.of("QR code path not set", true);

            Optional<String> oResultPath = args.stringV(2);

            String result;
            try {
                result = worker.readCode(oQRCodePath.get());
                Objects.requireNonNull(result);

            } catch (Exception e) {
                e.printStackTrace();

                return CommandResult.of("Fail to scan", true);
            }

            if (oResultPath.isPresent()) {
                try {
                    SimpleFileWorker.of(oResultPath.get()).writeToFile(Base64.getDecoder().decode(result));
                } catch (IOException e) {
                    e.printStackTrace();

                    return CommandResult.of("Fail to write result: " + result, true);
                }
            }else {
                return CommandResult.of(result);
            }
        }else {
            int argIndex = 1;

            Optional<String> oQRCodePath = args.stringV(argIndex++);

            if (oQRCodePath.isEmpty())
                return CommandResult.of("QR code path not set", true);

            Optional<Integer> oPixels = args.intV(argIndex++);

            if (oPixels.isEmpty())
                return CommandResult.of("Pixels width not set", true);

            Optional<String> oData = args.stringV(argIndex++);

            if (oData.isEmpty())
                return CommandResult.of("Text or path not set", true);

            String startData = oData.get();

            Optional<ErrorCorrectionLevel> level = Arrays.stream(ErrorCorrectionLevel.values())
                    .filter((f) -> f.name().equals(startData.toUpperCase()))
                    .findFirst();

            if (level.isPresent()) {
                oData = args.stringV(argIndex++);

                if (oData.isEmpty())
                    return CommandResult.of("Text or path not set", true);
            }

            StringBuilder data = new StringBuilder(oData.get());

            try {
                if (data.toString().startsWith("f:")){
                    String path = data.substring(2);

                    if (level.isPresent())
                        generateFromFile(oQRCodePath.get(), path, oPixels.get(), level.get());
                    else
                        generateFromFile(oQRCodePath.get(), path, oPixels.get());
                }else {
                    for (int i = argIndex; i < args.size(); i++)
                        data.append(" ").append(args.stringV(i).get());

                    if (level.isPresent())
                        generate(oQRCodePath.get(), data.toString(), oPixels.get(), level.get());
                    else
                        generate(oQRCodePath.get(), data.toString(), oPixels.get());
                }
            } catch (Exception e) {
                e.printStackTrace();

                return CommandResult.of("Failed", true);
            }
        }

        return CommandResult.of("Done");
    }

    public void generate(String resultPath, String text, int pixelsWidth, ErrorCorrectionLevel level) throws IOException, WriterException {
        worker.generateCode(text, resultPath, pixelsWidth, pixelsWidth, level);
    }

    public void generate(String resultPath, String text, int pixelsWidth) throws IOException, WriterException {
        generate(resultPath, text, pixelsWidth, ErrorCorrectionLevel.L);
    }

    public void generateFromFile(String resultPath, String sourcePath, int pixelsWidth) throws IOException, WriterException {
        generateFromFile(resultPath, sourcePath, pixelsWidth, ErrorCorrectionLevel.L);
    }

    public void generateFromFile(String resultPath, String sourcePath, int pixelsWidth, ErrorCorrectionLevel level) throws IOException, WriterException {
        generate(resultPath, Base64.getEncoder().encodeToString(SimpleFileWorker.of(sourcePath).readBytesFromFile()), pixelsWidth, level);
    }

    @Override
    public String description() {
        return "Scan or generate image code";
    }

    @Override
    public String args() {
        return "scan <image path> [result path] | generate <result path> <pixels width> [error correction level] <source text/f:path>";
    }

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .subTree().addPossibleArg("scan")

                .recursiveSubTree()
                .addCompleter(new Completers.FilesCompleter(Path.of("./")))
                .addTip("[result path]", "Result path to file")
                .parent()

                .parent()

                .subTree().addPossibleArg("generate")
                .addCompleter(new Completers.FilesCompleter(Path.of("./")))
                .addTip("<pixels width>", "Pixels width result image")
                .addTip("[error correction level]", "Correction level")
                .addTip("<source text/f:path>", "Content of result image. f: for select file")
                .parent()

                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.BACKUPS;
    }
}