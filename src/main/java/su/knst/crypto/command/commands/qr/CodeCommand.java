package su.knst.crypto.command.commands.qr;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.jline.builtins.Completers;
import su.knst.crypto.Main;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.args.ArgsTreeBuilder;
import su.knst.crypto.utils.codes.AbstractCodeWorker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

public class CodeCommand extends Command {

    protected final AbstractCodeWorker worker;
    public CodeCommand(AbstractCodeWorker worker) {
        this.worker = worker;
    }

    @Override
    public CommandResult run(ParamsContainer args) {
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

            String result;
            try {
                result = worker.readCode(oQRCodePath.get());
                Objects.requireNonNull(result);

            } catch (Exception e) {
                e.printStackTrace();

                return CommandResult.error("Fail to scan");
            }

            if (oResultPath.isPresent()) {
                try {
                    Files.write(oResultPath.get(), Base64.getDecoder().decode(result));
                } catch (IOException e) {
                    e.printStackTrace();

                    return CommandResult.error("Fail to write result: " + result);
                }
            }else {
                return CommandResult.of(result);
            }
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

                    if (level.isPresent())
                        generateFromFile(oQRCodePath.get(), path, oPixels.get(), level.get());
                    else
                        generateFromFile(oQRCodePath.get(), path, oPixels.get());
                }else {
                    for (int i = argIndex; i < args.size(); i++)
                        data.append(" ").append(args.stringV(i).orElseThrow());

                    if (level.isPresent())
                        generate(oQRCodePath.get(), data.toString(), oPixels.get(), level.get());
                    else
                        generate(oQRCodePath.get(), data.toString(), oPixels.get());
                }
            } catch (Exception e) {
                e.printStackTrace();

                return CommandResult.error("Failed");
            }
        }

        return CommandResult.of("Done");
    }

    public void generate(String resultPath, String text, int pixelsSide, ErrorCorrectionLevel level) throws IOException, WriterException {
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
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .addTip("[result path]", "Result path to file")
                .parent()

                .parent()

                .subTree().addPossibleArg("generate")

                .recursiveSubTree()
                .addTip("[result path]", "Result path to file")
                .addTip("<pixels width>", "Pixels width result image")

                .subTree()
                .addPossibleArgs(
                        Arrays.stream(ErrorCorrectionLevel.values())
                                .map(v -> v.name().toLowerCase())
                                .toList()
                )
                .parent()

                .addTip("<source text/f:path>", "Content of result image. f: for select file")
                .parent()

                .parent()

                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.BACKUPS;
    }
}
