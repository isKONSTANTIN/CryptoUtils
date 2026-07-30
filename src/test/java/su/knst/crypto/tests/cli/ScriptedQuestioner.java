package su.knst.crypto.tests.cli;

import su.knst.crypto.cli.Questioner;
import su.knst.crypto.utils.TerminalQuestion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Answers a command's questions from a fixed script, so any dialog can be driven end to end without
 * a terminal. This is where the old scripted argument mode went: out of production code and into a
 * test double.
 *
 * Running out of answers is the same as the user pressing enter on an empty prompt, which every
 * command reads as "cancel".
 */
public class ScriptedQuestioner implements Questioner {
    private final Deque<String> answers = new ArrayDeque<>();
    private final List<String> asked = new ArrayList<>();

    public ScriptedQuestioner(String... answers) {
        this.answers.addAll(List.of(answers));
    }

    public ScriptedQuestioner(List<String> answers) {
        this.answers.addAll(answers);
    }

    @Override
    public Optional<String> ask(TerminalQuestion question) {
        asked.add(question.text());

        return Optional.ofNullable(answers.poll()).filter(answer -> !answer.isEmpty());
    }

    /** The prompt texts seen so far, for asserting on the shape of a dialog. */
    public List<String> asked() {
        return List.copyOf(asked);
    }

    public boolean exhausted() {
        return answers.isEmpty();
    }
}
