package su.knst.crypto.command.commands.misc;

import su.knst.crypto.Main;
import su.knst.crypto.cli.Ask;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;

public class ExitCommand extends Command {
    @Override
    @SuppressWarnings("SameReturnValue")
    public CommandResult run(Ask in) {
        Main.shutdown();

        return CommandResult.VOID;
    }

    @Override
    public String description() {
        return "Exit from CryptoUtils";
    }
}
