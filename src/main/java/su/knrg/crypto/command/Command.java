package su.knrg.crypto.command;

import org.jline.builtins.Completers;
import org.jline.builtins.Completers.TreeCompleter.Node;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import su.knrg.crypto.command.commands.CommandTag;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.jline.builtins.Completers.TreeCompleter.node;

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
