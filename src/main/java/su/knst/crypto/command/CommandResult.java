package su.knst.crypto.command;

public record CommandResult(String message, boolean error) {

    public static CommandResult of(String message, boolean error) {
        return new CommandResult(message, error);
    }

    public static CommandResult of(String message) {
        return new CommandResult(message, false);
    }

    public static final CommandResult COMMAND_NOT_FOUND = CommandResult.of("Command not found", true);
    public static final CommandResult VOID = CommandResult.of("");
}
