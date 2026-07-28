package su.knst.crypto.tests.util;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.function.Supplier;

/**
 * Suppresses stderr output (e.g. printStackTrace() calls inside production code)
 * while running code paths that are expected to throw/log a handled exception.
 */
public final class QuietStderr {

    private QuietStderr() {
    }

    public static <T> T run(Supplier<T> action) {
        PrintStream original = System.err;
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        try {
            return action.get();
        } finally {
            System.setErr(original);
        }
    }
}
