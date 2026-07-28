package su.knst.crypto.utils;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.List;

public class ConsoleBox {
    public static List<String> splitTrimmed(String content) {
        List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));

        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty())
            lines.remove(lines.size() - 1);

        return lines;
    }

    public static String box(String title, List<String> lines, AttributedStyle frameStyle) {
        int width = title.length();
        for (String line : lines)
            width = Math.max(width, line.length());

        AttributedStringBuilder result = new AttributedStringBuilder();

        result.style(frameStyle);
        appendTitledTop(result, title, width);

        for (String line : lines) {
            result.style(frameStyle);
            result.append("│ ");
            result.style(AttributedStyle.DEFAULT);
            result.append(padRight(line, width));
            result.style(frameStyle);
            result.append(" │\n");
        }

        result.style(frameStyle);
        result.append("└").append("─".repeat(width + 2)).append("┘");
        result.style(AttributedStyle.DEFAULT);

        return result.toAnsi();
    }

    private static void appendTitledTop(AttributedStringBuilder result, String title, int width) {
        int innerWidth = width + 2;
        String label = " " + title + " ";

        if (label.length() >= innerWidth) {
            result.append("┌").append("─".repeat(innerWidth)).append("┐\n");
            return;
        }

        int dashes = innerWidth - label.length();
        int left = dashes / 2;
        int right = dashes - left;

        result.append("┌").append("─".repeat(left)).append(label).append("─".repeat(right)).append("┐\n");
    }

    public static String padRight(String text, int width) {
        return text + " ".repeat(Math.max(0, width - text.length()));
    }

    public static String wrap(String text, int width, String indent) {
        String[] words = text.split(" ");
        StringBuilder result = new StringBuilder();
        StringBuilder line = new StringBuilder(indent);

        for (String word : words) {
            if (line.length() > indent.length() && line.length() + 1 + word.length() > width) {
                result.append(line).append("\n");
                line = new StringBuilder(indent);
            }

            if (line.length() > indent.length())
                line.append(" ");

            line.append(word);
        }

        result.append(line);

        return result.toString();
    }
}
