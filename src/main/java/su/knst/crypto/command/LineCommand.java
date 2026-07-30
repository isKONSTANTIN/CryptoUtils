package su.knst.crypto.command;

import su.knst.crypto.cli.Ask;

/**
 * A command that can also take its argument on the same line, for the few where typing
 * {@code cd ..} or {@code help backup} is plainly faster than being asked.
 *
 * Everything else prompts. Rather than let a typed argument be silently dropped, the handler tells
 * the user it was ignored.
 */
public abstract class LineCommand extends Command {
    /**
     * @param argument the text after the command name, or null when there was none - in which case
     *                 the command should ask for it through {@code in} as usual
     */
    public abstract CommandResult run(Ask in, String argument);

    @Override
    public CommandResult run(Ask in) {
        return run(in, null);
    }
}
