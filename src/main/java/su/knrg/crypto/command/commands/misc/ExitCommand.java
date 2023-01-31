package su.knrg.crypto.command.commands.misc;

import su.knrg.crypto.Main;
import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;

public class ExitCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        return run();
    }

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
