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

    // Commands returning true keep parsing arguments typed inline on the REPL line (e.g. help,
    // cd, exit) - everything else always drops into its own interactive prompt flow instead.
    public boolean supportsInlineArgs() {
        return false;
    }
}
