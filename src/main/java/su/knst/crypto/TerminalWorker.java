package su.knst.crypto;

import org.jline.builtins.Completers.TreeCompleter;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStyle;
import su.knst.crypto.cli.Questioner;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandHandler;
import su.knst.crypto.command.LineCommand;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.Panel;
import su.knst.crypto.utils.Banner;
import su.knst.crypto.utils.TerminalQuestion;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.jline.builtins.Completers.TreeCompleter.node;

public class TerminalWorker implements Questioner {
    protected Terminal terminal;
    protected PrintWriter writer;

    protected boolean run = true;

    protected final CommandHandler handler;

    public TerminalWorker(CommandHandler handler) {
        this.handler = handler;
        handler.setQuestioner(this);
    }

    protected TreeCompleter createTree() {
        ArrayList<TreeCompleter.Node> nodes = new ArrayList<>();

        handler.getCommands().forEach((n, c) -> {
            Optional<TreeCompleter.Node> commandNode = Optional.ofNullable(c.completerNode(n));

            nodes.add(commandNode.orElseGet(() -> node(n)));
        });

        return new TreeCompleter(nodes);
    }

    @Override
    public Optional<String> ask(TerminalQuestion question) {
        // no REPL running (e.g. a command invoked directly with no arguments, outside start()) -
        // there is no terminal to prompt on, so treat it the same as the user giving no answer
        if (terminal == null)
            return Optional.empty();

        List<String> answers = question.answers();

        LineReaderBuilder readerBuilder = LineReaderBuilder.builder()
                .terminal(terminal);

        if (question.completer() != null)
            readerBuilder.completer(question.completer());
        else if (answers != null)
            // a fixed set of valid answers should always be autocompletable, even when the
            // command didn't wire up an explicit completer for it
            readerBuilder.completer(new StringsCompleter(answers));

        LineReader reader = readerBuilder.build();

        StringBuilder prompt = new StringBuilder(question.text());

        if (answers != null) {
            // Multi-line questions (e.g. a choice list) get their [options] on a fresh line
            // instead of glued onto the end of the last listed line.
            prompt.append(prompt.indexOf("\n") >= 0 ? "\n" : " ");

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

            if (answer != null)
                answer = answer.trim();

            if (answers == null || answers.contains(answer))
                break;
        }

        return Optional.ofNullable(answer);
    }
    public void start() throws IOException {
        //Logger.getLogger("org.jline").setLevel(Level.ALL);

        // dumb(true) makes a non-interactive stdin - a piped transcript, a CI container with no
        // TERM - fall back to a plain terminal quietly instead of logging a warning at the user.
        // Commands still read their answers line by line, which is what lets the built binary be
        // smoke-tested end to end.
        terminal = TerminalBuilder.builder()
                .jansi(true)
                .dumb(true)
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

            CommandResult result = dispatch(line);

            printResult(result);
        }

    }

    // Typing a command name drops straight into its own question flow; the intro panel says so,
    // except for the few commands that take their argument on the line and answer immediately.
    private CommandResult dispatch(String line) {
        String trimmed = line.strip();

        if (trimmed.isEmpty())
            return CommandResult.VOID;

        String name = trimmed.split("\\s+", 2)[0];
        Command command = handler.getCommands().get(name);

        if (command != null && !(command instanceof LineCommand))
            printIntro(command, name);

        return handler.run(trimmed);
    }

    private void printIntro(Command command, String name) {
        String description = command.description();

        StringBuilder content = new StringBuilder();

        if (description != null && !description.isBlank())
            content.append(description).append("\n");

        content.append("Interactive mode - answer the prompts below (Ctrl+C to cancel).");

        writer.println(Panel.framed(name, content.toString(), AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE)).render(false));
        writer.flush();
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
