package su.knst.crypto.command;

public class CommandResult {
    public final String message;
    public final boolean error;

    public CommandResult(String message, boolean error) {
        this.message = message;
        this.error = error;
    }

    public static CommandResult of(String message, boolean error) {
        return new CommandResult(message, error);
    }

    public static CommandResult of(String message) {
        return new CommandResult(message, false);
    }

    public static final CommandResult COMMAND_NOT_FOUND = CommandResult.of("Command not found", true);
    public static final CommandResult VOID = CommandResult.of("");
}
