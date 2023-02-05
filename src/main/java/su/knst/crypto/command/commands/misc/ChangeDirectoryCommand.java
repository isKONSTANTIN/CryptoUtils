package su.knst.crypto.command.commands.misc;

import org.jline.builtins.Completers;
import su.knst.crypto.Main;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.utils.args.ArgsTreeBuilder;

import java.util.Optional;

public class ChangeDirectoryCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> oPath = args.stringV(0);

        if (oPath.isEmpty())
            return CommandResult.VOID;

        Main.changeCurrentPath(oPath.get());

        return handler.run("ls");
    }

    @Override
    public String description() {
        return "Change current directory";
    }

    @Override
    public String args() {
        return null;
    }

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .addCompleter(new Completers.DirectoriesCompleter(Main::getCurrentPath))

                .build();
    }
}
