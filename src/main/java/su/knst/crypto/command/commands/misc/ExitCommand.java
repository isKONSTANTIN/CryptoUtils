package su.knst.crypto.command.commands.misc;

import su.knst.crypto.Main;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;

public class ExitCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        return run();
    }

    @SuppressWarnings("SameReturnValue")
    public CommandResult run() {
        Main.shutdown();

        return CommandResult.VOID;
    }

    @Override
    public String description() {
        return "Exit from CryptoUtils";
    }

    @Override
    public String args() {
        return null;
    }
}
