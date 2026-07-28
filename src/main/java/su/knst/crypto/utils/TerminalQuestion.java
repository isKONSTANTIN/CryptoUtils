package su.knst.crypto.utils;

import org.jline.reader.Completer;

import java.util.List;

public record TerminalQuestion(String text, List<String> answers, Completer completer) {
    public TerminalQuestion(String text, List<String> answers) {
        this(text, answers, null);
    }
}
