package su.knrg.crypto.command.commands;

import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;

public class HelpCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        return run();
    }

    public CommandResult run() {
        return CommandResult.of(handler.helpPage());
    }

    @Override
    public String description() {
        return "Show list of commands";
    }

    @Override
    public String args() {
        return null;
    }
}
