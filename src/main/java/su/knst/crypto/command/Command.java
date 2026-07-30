package su.knst.crypto.command;

import org.jline.builtins.Completers.TreeCompleter.Node;
import su.knst.crypto.cli.Ask;
import su.knst.crypto.command.commands.CommandTag;

/**
 * One thing the user can do. A command collects answers through {@link Ask} and turns them into
 * calls on the core modules - it holds no logic of its own beyond the shape of the conversation.
 */
public abstract class Command {
    protected CommandHandler handler;

    public abstract CommandResult run(Ask in);

    void init(CommandHandler parent) {
        handler = parent;
    }

    public abstract String description();

    /** Completion tree for commands that take the rest of the line; see {@link LineCommand}. */
    public Node completerNode(String alias) {
        return null;
    }

    public CommandTag tag() {
        return CommandTag.MISC;
    }
}
