package su.knst.crypto.command;

import su.knst.crypto.utils.Prompts;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

// A single source of command input, abstracting over where the value actually comes from:
// positional command-line tokens (ScriptedArgSource) or interactive terminal prompts
// (InteractiveArgSource). Letting a command ask "give me the next X" through this interface
// instead of writing two parallel runScripted()/runInteractive() methods collapses both flows
// into one resolve(ArgSource) method per command.
public interface ArgSource {
    Optional<String> string(String prompt);

    // Like string(), but interactive mode offers file-path autocompletion (Tab) for inputs that
    // may accept a file path among other raw token forms.
    Optional<String> stringWithFileCompletion(String prompt);

    Optional<Integer> integer(String prompt);

    Optional<String> choice(String prompt, List<Prompts.Choice> choices);

    // Like choice(), but scripted mode may omit the token entirely: if the next positional token
    // doesn't match any choice value, defaultValue is used and the token is left unconsumed.
    // Interactive mode always asks and ignores defaultValue.
    Optional<String> choiceOr(String prompt, List<Prompts.Choice> choices, String defaultValue);

    Optional<Path> existingFilePath(String prompt);

    Optional<Path> newFilePath(String prompt);

    Optional<Path> directory(String prompt);

    // Consumes every remaining token as individual words (e.g. a mnemonic phrase).
    Optional<String[]> words(String prompt);

    // Consumes every remaining token joined by spaces into a single free-text answer.
    Optional<String> restOfLine(String prompt);

    // True for InteractiveArgSource. Only needed by the handful of commands whose scripted and
    // interactive flows read a variable-length list differently (e.g. "consume until the
    // positional args run out" vs "ask how many, then loop that many times").
    boolean interactive();
}
