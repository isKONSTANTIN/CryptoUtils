package su.knst.crypto.cli;

import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.TerminalQuestion;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Every question a command can put to the user.
 *
 * Validation lives here rather than in the commands: a value that doesn't fit is a reason to ask
 * again, not to fail the command halfway through a dialog. An empty answer always means "cancel",
 * and comes back as an empty Optional.
 */
public final class Ask {
    private final Questioner questioner;

    public Ask(Questioner questioner) {
        this.questioner = questioner;
    }

    public Optional<String> string(String prompt) {
        return questioner.ask(new TerminalQuestion(prompt, null)).filter(value -> !value.isBlank());
    }

    /** Free text with file-path completion, for answers that may be a path or something else. */
    public Optional<String> stringWithFileCompletion(String prompt) {
        return Prompts.askStringWithFileCompletion(questioner, prompt);
    }

    public Optional<Integer> integer(String prompt) {
        return Prompts.askInt(questioner, prompt);
    }

    /** Re-asks until the answer lands inside the range, so callers never see an invalid number. */
    public Optional<Integer> integer(String prompt, int min, int max) {
        while (true) {
            Optional<Integer> answer = Prompts.askInt(questioner, prompt + " [" + min + "-" + max + "]");

            if (answer.isEmpty())
                return Optional.empty();

            if (answer.get() >= min && answer.get() <= max)
                return answer;
        }
    }

    /** Re-asks until the answer is valid hex. */
    public Optional<String> hex(String prompt) {
        while (true) {
            Optional<String> answer = string(prompt);

            if (answer.isEmpty())
                return Optional.empty();

            String hex = answer.get().trim();

            if (HexUtils.isValidHex(hex))
                return Optional.of(hex);
        }
    }

    public Optional<Boolean> confirm(String prompt) {
        return questioner.ask(new TerminalQuestion(prompt, List.of("y", "n")))
                .map(answer -> answer.equalsIgnoreCase("y"));
    }

    public Optional<String> choice(String prompt, List<Prompts.Choice> choices) {
        return Prompts.askChoice(questioner, prompt, choices);
    }

    /** Re-asks until an existing file is named. */
    public Optional<Path> existingFile(String prompt) {
        return Prompts.askExistingFilePath(questioner, prompt);
    }

    public Optional<Path> newFile(String prompt) {
        return Prompts.askNewFilePath(questioner, prompt);
    }

    /** Space-separated words, completed against the active BIP-39 wordlist. */
    public Optional<String[]> words(String prompt) {
        return Prompts.askWords(questioner, prompt);
    }
}
