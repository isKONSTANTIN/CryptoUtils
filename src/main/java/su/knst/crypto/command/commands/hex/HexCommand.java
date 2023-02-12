package su.knst.crypto.command.commands.hex;

import org.jline.builtins.Completers;
import su.knst.crypto.Main;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.args.ArgsTreeBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class HexCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
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

        boolean mode = oMode.map((s) -> s.equals("encode")).get();

        if (mode) {
            byte[] bytes;

            try {
                bytes = Files.readAllBytes(oSource.get());
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.error("Failed to read source file");
            }

            try {
                Files.writeString(oResult.get(), HexUtils.bytesToHex(bytes));
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.error("Failed to write result file");
            }
        }else {
            String hex;

            try {
                hex = Files.readString(oSource.get());
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.error("Failed to read source file");
            }

            try {
                Files.write(oResult.get(), HexUtils.hexStringToByteArray(hex));
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.error("Failed to write result file");
            }
        }

        return CommandResult.of("Done!");
    }



    @Override
    public String description() {
        return "Convert files to simple hex text-file";
    }

    @Override
    public String args() {
        return "<encode/decode> <source path> <result path>";
    }

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .subTree().addPossibleArg("encode")

                .recursiveSubTree()
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .parent()

                .parent()

                .subTree().addPossibleArg("decode")
                .recursiveSubTree()
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .parent()
                .parent()

                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.BACKUPS;
    }
}
