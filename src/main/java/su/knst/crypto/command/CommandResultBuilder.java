package su.knst.crypto.command;

public class CommandResultBuilder {
    protected StringBuilder stringBuilder = new StringBuilder();
    protected boolean error;

    public static CommandResultBuilder builder() {
        return new CommandResultBuilder();
    }

    CommandResultBuilder() {
    }

    public CommandResultBuilder line(String line) {
        return append(line).append("\n");
    }
    public CommandResultBuilder line() {
        return append("\n");
    }

    public CommandResultBuilder append(String text) {
        stringBuilder.append(text);

        return this;
    }

    public CommandResultBuilder error(boolean isErrored) {
        error = isErrored;

        return this;
    }

    public CommandResultBuilder error() {
        return error(true);
    }

    public CommandResultBuilder merge(CommandResult result) {
        if (result.error())
            error();

        return append(result.message());
    }

    public CommandResult build() {
        return new CommandResult(stringBuilder.toString(), error);
    }
}
