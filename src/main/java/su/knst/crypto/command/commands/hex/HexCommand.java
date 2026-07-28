package su.knst.crypto.command.commands.hex;

import su.knst.crypto.Main;
import su.knst.crypto.TerminalWorker;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.FileUtils;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.Prompts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class HexCommand extends Command {
    private static final List<Prompts.Choice> MODE_CHOICES = List.of(
            new Prompts.Choice("encode", "Encode", "Convert a binary file into a hex text file"),
            new Prompts.Choice("decode", "Decode", "Convert a hex text file back into binary")
    );

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

        if (!(oMode.get().equals("encode") || oMode.get().equals("decode")))
            return CommandResult.error("Mode must be 'encode' or 'decode'");

        Optional<Path> oSource = args.stringV(1).map((p) -> Main.getCurrentPath().resolve(p));

        if (oSource.isEmpty())
            return CommandResult.error("Source path not set");

        Optional<Path> oResult = args.stringV(2).map((p) -> Main.getCurrentPath().resolve(p));

        if (oResult.isEmpty())
            return CommandResult.error("Result path not set");

        return convert(oMode.get().equals("encode"), oSource.get(), oResult.get());
    }

    private CommandResult runInteractive() {
        TerminalWorker tw = Main.getTerminalWorker();

        Optional<String> oMode = Prompts.askChoice(tw, "Convert direction?", MODE_CHOICES);

        if (oMode.isEmpty())
            return CommandResult.error("No input");

        Optional<Path> oSource = Prompts.askExistingFilePath(tw, "Source file path?");

        if (oSource.isEmpty())
            return CommandResult.error("No input");

        Optional<Path> oResult = Prompts.askNewFilePath(tw, "Result file path?");

        if (oResult.isEmpty())
            return CommandResult.error("No input");

        return convert(oMode.get().equals("encode"), oSource.get(), oResult.get());
    }

    private CommandResult convert(boolean encode, Path source, Path result) {
        if (encode) {
            byte[] bytes;

            try {
                bytes = Files.readAllBytes(source);
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.error("Failed to read source file");
            }

            try {
                FileUtils.writeOwnerOnly(result, HexUtils.bytesToHex(bytes));
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.error("Failed to write result file");
            }
        } else {
            String hex;

            try {
                hex = Files.readString(source);
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.error("Failed to read source file");
            }

            try {
                FileUtils.writeOwnerOnly(result, HexUtils.hexStringToByteArray(hex));
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.error("Failed to write result file");
            }
        }

        return CommandResult.of("Done!");
    }

    @Override
    public String description() {
        return "Convert files to and from a simple hex text representation";
    }

    @Override
    public String args() {
        return "<encode/decode> <source path> <result path>";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.BACKUPS;
    }
}
