package su.knst.crypto.cli;

import su.knst.crypto.utils.TerminalQuestion;

import java.util.Optional;

/**
 * Whatever answers a question. The REPL's {@code TerminalWorker} is the real one; tests supply a
 * scripted stand-in, which is what keeps every command's dialog runnable without a terminal.
 *
 * @return the answer, or empty when the user gave none (cancelled, or nothing to give)
 */
@FunctionalInterface
public interface Questioner {
    Optional<String> ask(TerminalQuestion question);
}
