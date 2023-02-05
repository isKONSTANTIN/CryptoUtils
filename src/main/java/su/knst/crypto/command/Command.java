package su.knst.crypto.command;

import org.jline.builtins.Completers.TreeCompleter.Node;
import su.knst.crypto.command.commands.CommandTag;

public abstract class Command {
    protected CommandHandler handler;

    public abstract CommandResult run(ParamsContainer args);

    void init(CommandHandler parent) {
        handler = parent;
    }

    public abstract String description();
    public abstract String args();

    public Node getArgsTree(String alias) {
        return null;
    }

    public CommandTag tag() {
        return CommandTag.MISC;
    }
}
