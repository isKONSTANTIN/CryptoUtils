package su.knrg.crypto.command;

import su.knrg.crypto.command.commands.CommandTag;

public abstract class Command {
    protected CommandHandler handler;

    public abstract CommandResult run(ParamsContainer args);

    void init(CommandHandler parent) {
        handler = parent;
    }

    public abstract String description();
    public abstract String args();

    public CommandTag tag() {
        return CommandTag.MISC;
    }
}
