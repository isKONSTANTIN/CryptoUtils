package su.knst.crypto.utils;

import org.jline.builtins.Completers;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import su.knst.crypto.Main;
import su.knst.crypto.TerminalWorker;
import su.knst.crypto.utils.worldlists.WordLists;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// Shared question helpers built on top of TerminalWorker.ask, so every command's interactive
// mode gets the same retry behavior, file/word autocompletion and choice formatting for free.
public final class Prompts {
    private Prompts() {
    }

    public static Optional<Integer> askInt(TerminalWorker tw, String question) {
        while (true) {
            Optional<String> answer = tw.ask(new TerminalQuestion(question, null));

            if (answer.isEmpty())
                return Optional.empty();

            try {
                return Optional.of(Integer.parseInt(answer.get().trim()));
            } catch (NumberFormatException ignored) {
                // ask again
            }
        }
    }

    public record Choice(String value, String label, String description) {
        public Choice(String value, String label) {
            this(value, label, null);
        }
    }

    public static Optional<String> askChoice(TerminalWorker tw, String question, List<Choice> choices) {
        int valueWidth = choices.stream().mapToInt(c -> c.value().length()).max().orElse(0);

        StringBuilder text = new StringBuilder(question);
        for (Choice choice : choices) {
            text.append("\n  ").append(padRight(choice.value(), valueWidth)).append(" - ").append(choice.label());

            if (choice.description() != null && !choice.description().isBlank())
                text.append(" (").append(choice.description()).append(")");
        }

        List<String> values = choices.stream().map(Choice::value).toList();

        return tw.ask(new TerminalQuestion(text.toString(), values));
    }

    private static String padRight(String s, int width) {
        return s + " ".repeat(Math.max(0, width - s.length()));
    }

    // Loops until an existing file is given, or the user cancels with an empty answer.
    public static Optional<Path> askExistingFilePath(TerminalWorker tw, String question) {
        Completer completer = new Completers.FilesCompleter(Main::getCurrentPath);

        while (true) {
            Optional<String> answer = tw.ask(new TerminalQuestion(question, null, completer));

            if (answer.isEmpty() || answer.get().isBlank())
                return Optional.empty();

            Path path = Main.getCurrentPath().resolve(answer.get().trim());

            if (path.toFile().isFile())
                return Optional.of(path);
            // not a file yet - ask again instead of failing the whole command
        }
    }

    // For output/destination paths that are expected not to exist yet (e.g. generated keys).
    public static Optional<Path> askNewFilePath(TerminalWorker tw, String question) {
        Completer completer = new Completers.FilesCompleter(Main::getCurrentPath);

        Optional<String> answer = tw.ask(new TerminalQuestion(question, null, completer));

        if (answer.isEmpty() || answer.get().isBlank())
            return Optional.empty();

        return Optional.of(Main.getCurrentPath().resolve(answer.get().trim()));
    }

    public static Optional<Path> askDirectory(TerminalWorker tw, String question) {
        Completer completer = new Completers.DirectoriesCompleter(Main::getCurrentPath);

        Optional<String> answer = tw.ask(new TerminalQuestion(question, null, completer));

        if (answer.isEmpty() || answer.get().isBlank())
            return Optional.empty();

        return Optional.of(Main.getCurrentPath().resolve(answer.get().trim()));
    }

    // Free-text words separated by spaces (mnemonic phrases), with completion against the
    // active wordlist for the word currently being typed.
    public static Optional<String[]> askWords(TerminalWorker tw, String question) {
        Completer completer = (reader, line, candidates) ->
                candidates.addAll(
                        Arrays.stream(WordLists.getActiveList().array())
                                .filter(s -> s.contains(line.word()))
                                .map(Candidate::new)
                                .toList()
                );

        Optional<String> answer = tw.ask(new TerminalQuestion(question, null, completer));

        if (answer.isEmpty() || answer.get().isBlank())
            return Optional.empty();

        return Optional.of(answer.get().trim().split("\\s+"));
    }
}
