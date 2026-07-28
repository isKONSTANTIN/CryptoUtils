package su.knst.crypto.command;

import su.knst.crypto.Main;
import su.knst.crypto.utils.Prompts;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

// Reads values positionally from a ParamsContainer, advancing an internal cursor with every
// call. Tokens are handed back raw and unfiltered, same as the old ParamsContainer.stringV -
// commands that use a "null" placeholder for a skipped slot (e.g. Shamir join parts) check for
// it themselves via string(), same as before.
public class ScriptedArgSource implements ArgSource {
    private final ParamsContainer args;
    private int cursor = 0;

    public ScriptedArgSource(ParamsContainer args) {
        this.args = args;
    }

    private Optional<String> next() {
        Optional<String> value = args.stringV(cursor);

        if (value.isPresent())
            cursor++;

        return value;
    }

    @Override
    public Optional<String> string(String prompt) {
        return next();
    }

    @Override
    public Optional<String> stringWithFileCompletion(String prompt) {
        return next();
    }

    @Override
    public Optional<Integer> integer(String prompt) {
        return next().flatMap(v -> {
            try {
                return Optional.of(Integer.parseInt(v.trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private static Optional<String> matchChoice(String token, List<Prompts.Choice> choices) {
        return choices.stream().map(Prompts.Choice::value).filter(v -> v.equalsIgnoreCase(token)).findFirst();
    }

    @Override
    public Optional<String> choice(String prompt, List<Prompts.Choice> choices) {
        return next().flatMap(v -> matchChoice(v, choices));
    }

    @Override
    public Optional<String> choiceOr(String prompt, List<Prompts.Choice> choices, String defaultValue) {
        Optional<String> peeked = args.stringV(cursor).flatMap(v -> matchChoice(v, choices));

        if (peeked.isPresent()) {
            cursor++;

            return peeked;
        }

        return Optional.of(defaultValue);
    }

    @Override
    public Optional<Path> existingFilePath(String prompt) {
        return next().map(p -> Main.getCurrentPath().resolve(p));
    }

    @Override
    public Optional<Path> newFilePath(String prompt) {
        return next().map(p -> Main.getCurrentPath().resolve(p));
    }

    @Override
    public Optional<Path> directory(String prompt) {
        return next().map(p -> Main.getCurrentPath().resolve(p));
    }

    @Override
    public Optional<String[]> words(String prompt) {
        if (cursor >= args.size())
            return Optional.empty();

        String[] words = new String[args.size() - cursor];

        for (int i = 0; i < words.length; i++)
            words[i] = args.stringV(cursor + i).orElseThrow();

        cursor = args.size();

        return Optional.of(words);
    }

    @Override
    public Optional<String> restOfLine(String prompt) {
        if (cursor >= args.size())
            return Optional.empty();

        StringBuilder text = new StringBuilder();

        for (int i = cursor; i < args.size(); i++)
            text.append(i > cursor ? " " : "").append(args.stringV(i).orElseThrow());

        cursor = args.size();

        return Optional.of(text.toString());
    }

    @Override
    public boolean interactive() {
        return false;
    }
}
