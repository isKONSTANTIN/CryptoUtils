package su.knst.crypto.utils;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import su.knst.crypto.Main;

public class Banner {
    private static final String[] LOGO = {
            "░█▀▀░█▀▄░█░█░█▀█░▀█▀░█▀█░░░█░█░▀█▀░▀█▀░█░░░█▀▀░",
            "░█░░░█▀▄░░█░░█▀▀░░█░░█░█░░░█░█░░█░░░█░░█░░░▀▀█░",
            "░▀▀▀░▀░▀░░▀░░▀░░░░▀░░▀▀▀░░░▀▀▀░░▀░░▀▀▀░▀▀▀░▀▀▀░",
    };

    private static final String[] WARNING = {
            "This software is provided \"AS IS\", WITHOUT WARRANTY OF ANY KIND.",
            "Use at your own risk.",
    };

    public static String render(Terminal terminal) {
        AttributedStringBuilder builder = new AttributedStringBuilder();

        String version = "v." + Main.getVersion();
        String lastLogoLine = LOGO[LOGO.length - 1] + " " + version;

        int contentWidth = 0;
        for (String line : LOGO) {
            contentWidth = Math.max(contentWidth, line.length());
        }
        contentWidth = Math.max(contentWidth, lastLogoLine.length());
        for (String line : WARNING) {
            contentWidth = Math.max(contentWidth, line.length());
        }

        AttributedStyle borderStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
        AttributedStyle logoStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold();
        AttributedStyle versionStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold();
        AttributedStyle warningStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);

        appendBorder(builder, borderStyle, contentWidth, true);
        for (int i = 0; i < LOGO.length - 1; i++) {
            appendContentLine(builder, borderStyle, logoStyle, LOGO[i], contentWidth);
        }
        appendLogoWithVersion(builder, borderStyle, logoStyle, versionStyle, LOGO[LOGO.length - 1], version, contentWidth);
        appendSeparator(builder, borderStyle, contentWidth);
        for (String line : WARNING) {
            appendContentLine(builder, borderStyle, warningStyle, line, contentWidth);
        }
        appendBorder(builder, borderStyle, contentWidth, false);

        builder.style(AttributedStyle.DEFAULT);

        return builder.toAnsi(terminal);
    }

    private static void appendBorder(AttributedStringBuilder builder, AttributedStyle borderStyle, int contentWidth, boolean top) {
        builder.style(borderStyle);
        builder.append(top ? "╔" : "╚");
        builder.append("═".repeat(contentWidth + 2));
        builder.append(top ? "╗" : "╝");
        builder.append("\n");
    }

    private static void appendSeparator(AttributedStringBuilder builder, AttributedStyle borderStyle, int contentWidth) {
        builder.style(borderStyle);
        builder.append("╠");
        builder.append("═".repeat(contentWidth + 2));
        builder.append("╣");
        builder.append("\n");
    }

    private static void appendContentLine(AttributedStringBuilder builder, AttributedStyle borderStyle, AttributedStyle textStyle, String text, int contentWidth) {
        builder.style(borderStyle);
        builder.append("║ ");
        builder.style(textStyle);
        builder.append(text);
        builder.append(" ".repeat(contentWidth - text.length()));
        builder.style(borderStyle);
        builder.append(" ║");
        builder.append("\n");
    }

    private static void appendLogoWithVersion(AttributedStringBuilder builder, AttributedStyle borderStyle, AttributedStyle logoStyle, AttributedStyle versionStyle, String logoLine, String version, int contentWidth) {
        builder.style(borderStyle);
        builder.append("║ ");
        builder.style(logoStyle);
        builder.append(logoLine);
        builder.style(versionStyle);
        builder.append(" ").append(version);
        int used = logoLine.length() + 1 + version.length();
        builder.style(borderStyle);
        builder.append(" ".repeat(contentWidth - used));
        builder.append(" ║");
        builder.append("\n");
    }
}
