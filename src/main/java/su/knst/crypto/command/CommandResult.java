package su.knst.crypto.command;

import org.jline.utils.AttributedStyle;
import su.knst.crypto.utils.ConsoleBox;

import java.util.ArrayList;
import java.util.List;

public class CommandResult extends Panel {
    private final boolean error;
    private final List<Panel> panels;

    private CommandResult(String title, boolean framed, AttributedStyle style, String content, List<Panel> panels, boolean error) {
        super(title, framed, style, content);

        this.panels = panels;
        this.error = error;
    }

    public static CommandResult of(String message, boolean error) {
        return new CommandResult(null, true, null, message, List.of(), error);
    }

    public static CommandResult of(String message) {
        return of(message, false);
    }

    public static CommandResult error(String message) {
        return of(message, true);
    }

    public static CommandResult plain(String message, boolean error) {
        return new CommandResult(null, false, null, message, List.of(), error);
    }

    public static CommandResult plain(String message) {
        return plain(message, false);
    }

    public static CommandResult plainError(String message) {
        return plain(message, true);
    }

    public static CommandResult panels(List<Panel> panels) {
        return new CommandResult(null, false, null, null, panels, false);
    }

    public boolean error() {
        return error;
    }

    public List<Panel> panels() {
        return panels;
    }

    public CommandResult withTitle(String newTitle) {
        if (title != null)
            return this;

        return new CommandResult(newTitle, framed, style, content, panels, error);
    }

    /** Appends a note about something the command did not act on, without changing the outcome. */
    public CommandResult withNotice(String notice) {
        List<Panel> withNotice = new ArrayList<>(panels);
        withNotice.add(Panel.plain(notice));

        return new CommandResult(title, framed, style, content, withNotice, error);
    }

    public String message() {
        StringBuilder sb = new StringBuilder(plainText());

        for (Panel panel : panels) {
            if (sb.length() > 0)
                sb.append("\n");

            sb.append(panel.plainText());
        }

        return sb.toString();
    }

    @Override
    public String render(boolean inheritedError) {
        boolean errored = error || inheritedError;

        StringBuilder body = new StringBuilder(plainText());

        for (Panel panel : panels) {
            if (body.length() > 0)
                body.append("\n\n");

            body.append(panel.render(errored));
        }

        if (!framed)
            return body.toString();

        AttributedStyle s = style != null
                ? style
                : (errored ? AttributedStyle.DEFAULT.foreground(AttributedStyle.RED) : AttributedStyle.DEFAULT);

        return ConsoleBox.box(title != null ? title : "", ConsoleBox.splitTrimmed(body.toString()), s);
    }

    public static final CommandResult COMMAND_NOT_FOUND = CommandResult.plainError("Command not found");
    public static final CommandResult VOID = CommandResult.plain("");
}
