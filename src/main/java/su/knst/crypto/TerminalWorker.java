package su.knst.crypto;

import org.jline.builtins.Completers.TreeCompleter;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import su.knst.crypto.command.CommandHandler;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.utils.Banner;
import su.knst.crypto.utils.TerminalQuestion;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.jline.builtins.Completers.TreeCompleter.node;

public class TerminalWorker {
    protected Terminal terminal;
    protected PrintWriter writer;

    protected boolean run = true;

    protected final CommandHandler handler;

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

    public Optional<String> ask(TerminalQuestion question) {
        List<String> answers = question.answers();

        LineReaderBuilder readerBuilder = LineReaderBuilder.builder()
                .terminal(terminal);

        LineReader reader = readerBuilder.build();

        StringBuilder prompt = new StringBuilder(question.text()).append(" ");

        if (answers != null) {
            prompt.append("[");
            for (String s : answers)
                prompt.append(s).append("/");

            prompt.deleteCharAt(prompt.length() - 1);
            prompt.append("]");
        }

        prompt.append(" ");

        String answer = null;
        while (true) {
            try {
                answer = reader.readLine(prompt.toString());
            } catch (Exception e) {
                break;
            }

            if (answers == null || answers.contains(answer))
                break;
        }

        return Optional.ofNullable(answer);
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

        writer.println(Banner.render(terminal));
        printResult(handler.run("help"));

        terminal.handle(Terminal.Signal.WINCH, signal -> {
            writer.println(Banner.render(terminal));
            writer.flush();
            reader.callWidget(LineReader.REDISPLAY);
        });

        while (run) {
            String line;

            try {
                line = reader.readLine("cu> ");
            } catch (UserInterruptException | EndOfFileException e) {
                run = false;
                break;
            } catch (Exception e) {
                e.printStackTrace();
                run = false;
                break;
            }

            CommandResult result = handler.run(line);

            printResult(result);
        }

    }

    private void printResult(CommandResult result) {
        String rendered = result.render(false);

        if (!rendered.isBlank())
            writer.println(rendered);
    }

    public void stop() {
        run = false;
    }
}
