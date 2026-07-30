package su.knst.crypto.command;

import su.knst.crypto.TerminalWorker;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.TerminalQuestion;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

// Asks the user for every value through TerminalWorker/Prompts, the same low-level helpers
// commands used to call directly from their own runInteractive() methods.
public class InteractiveArgSource implements ArgSource {
    private final TerminalWorker tw;

    public InteractiveArgSource(TerminalWorker tw) {
        this.tw = tw;
    }

    @Override
    public Optional<String> string(String prompt) {
        return tw.ask(new TerminalQuestion(prompt, null)).filter(v -> !v.isBlank());
    }

    @Override
    public Optional<String> stringWithFileCompletion(String prompt) {
        return Prompts.askStringWithFileCompletion(tw, prompt);
    }

    @Override
    public Optional<Integer> integer(String prompt) {
        return Prompts.askInt(tw, prompt);
    }

    @Override
    public Optional<String> choice(String prompt, List<Prompts.Choice> choices) {
        return Prompts.askChoice(tw, prompt, choices);
    }

    @Override
    public Optional<String> choiceOr(String prompt, List<Prompts.Choice> choices, String defaultValue) {
        return choice(prompt, choices);
    }

    @Override
    public Optional<Path> existingFilePath(String prompt) {
        return Prompts.askExistingFilePath(tw, prompt);
    }

    @Override
    public Optional<Path> newFilePath(String prompt) {
        return Prompts.askNewFilePath(tw, prompt);
    }

    @Override
    public Optional<String[]> words(String prompt) {
        return Prompts.askWords(tw, prompt);
    }

    @Override
    public Optional<String> restOfLine(String prompt) {
        return string(prompt);
    }

    @Override
    public boolean interactive() {
        return true;
    }
}
