package su.knst.crypto.utils;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import su.knst.crypto.Main;

public class Banner {
    private static final String LOGO =
            "   ___                 _        _   _ _   _ _\n" +
            "  / __\\_ __ _   _ _ __ | |_ ___ | | | | |_(_) |___\n" +
            " / /  | '__| | | | '_ \\| __/ _ \\| | | | __| | / __|\n" +
            "/ /___| |  | |_| | |_) | || (_) | |_| | |_| | \\__ \\\n" +
            "\\____/|_|   \\__, | .__/ \\__\\___/ \\___/ \\__|_|_|___/\n" +
            "            |___/|_|";

    public static String render(Terminal terminal) {
        AttributedStringBuilder builder = new AttributedStringBuilder();

        builder.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold());
        builder.append(LOGO).append("\n");
        builder.style(AttributedStyle.DEFAULT);

        builder.append("v").append(Main.getVersion()).append("\n\n");

        builder.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
        builder.append("This software is provided \"AS IS\", WITHOUT WARRANTY OF ANY KIND.\n");
        builder.append("Use at your own risk. Type 'help' for available commands.\n");
        builder.style(AttributedStyle.DEFAULT);

        return builder.toAnsi(terminal);
    }
}
