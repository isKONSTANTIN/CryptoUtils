package su.knrg.crypto;

import org.jline.builtins.Completers.TreeCompleter;
import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.Log;
import su.knrg.crypto.command.CommandHandler;
import su.knrg.crypto.command.CommandResult;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jline.builtins.Completers.TreeCompleter.node;

public class TerminalWorker {
    protected Terminal terminal;
    protected PrintWriter writer;
    protected LineReader reader;

    protected boolean run = true;

    protected CommandHandler handler;

    public TerminalWorker(CommandHandler handler) {
        this.handler = handler;
    }

    protected TreeCompleter createTree() {
        ArrayList<TreeCompleter.Node> nodes = new ArrayList<>();

        handler.getCommands().forEach((n, c) -> {
            Optional<TreeCompleter.Node> commandNode = Optional.ofNullable(c.getArgsTree(n));

            nodes.add(commandNode.orElseGet(() -> node(n)));
        });

        return new TreeCompleter(nodes);
    }

    public void start() throws IOException {
        //Logger.getLogger("org.jline").setLevel(Level.ALL);

        terminal = TerminalBuilder.builder()
                .jansi(true)
                .build();
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(createTree())
                .build();

        writer = terminal.writer();

        writer.println(handler.run("help").message);

        while (run) {
            String line;

            try {
                line = reader.readLine("cu> ");
            } catch (Exception e) {
                e.printStackTrace();
                run = false;
                break;
            }

            CommandResult result = handler.run(line);

            writer.println(result.message);
        }

    }

    public void stop() {
        run = false;
    }
}
