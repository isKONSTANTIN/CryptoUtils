package su.knst.crypto.cli.format;

import su.knst.crypto.command.Panel;
import su.knst.crypto.core.seed.SeedView;

import java.util.ArrayList;
import java.util.List;

/** Turns seed values into the console output the seed command prints. */
public final class SeedFormat {
    private interface MnemonicFormatter {
        String format(String[] words);
    }

    private SeedFormat() {
    }

    public static List<Panel> panels(SeedView view) {
        List<Panel> panels = new ArrayList<>();

        panels.add(Panel.framed("Source entropy",
                formatBits(view.entropy(), 4) + "\n"
                        + "Base64 encoded: " + view.base64() + "\n"
                        + "Hex encoded: " + view.hex(),
                null));

        String inline = joinSeedSections(view, SeedFormat::formatMnemonicLine);
        if (!inline.isEmpty())
            panels.add(Panel.framed("Seed phrase", inline, null));

        String vertical = joinSeedSections(view, SeedFormat::formatMnemonicList);
        if (!vertical.isEmpty())
            panels.add(Panel.plain(vertical));

        return panels;
    }

    private static String joinSeedSections(SeedView view, MnemonicFormatter formatter) {
        StringBuilder result = new StringBuilder();

        if (view.mnemonic12() != null)
            result.append("12-word seed:\n").append(formatter.format(view.mnemonic12()));

        if (view.mnemonic24() != null)
            result.append("\n24-word seed:\n").append(formatter.format(view.mnemonic24()));

        return result.toString();
    }

    public static String formatMnemonic(String[] words) {
        return formatMnemonicList(words) + "\n" + formatMnemonicLine(words);
    }

    public static String formatMnemonicList(String[] words) {
        StringBuilder list = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            list
                    .append(i + 1)
                    .append(". ")
                    .append(i + 1 < 10 ? " " : "")
                    .append(words[i])
                    .append("\n");
        }

        return list.toString();
    }

    public static String formatMnemonicLine(String[] words) {
        return String.join(" ", words);
    }

    public static String formatBits(byte[] bytes, int bytesInLine) {
        int i = 0;
        StringBuilder builder = new StringBuilder();

        for (byte b : bytes) {
            i++;

            builder.append(String.format("%8s", Integer.toBinaryString(b & 0xFF))
                            .replace(' ', '0'))
                    .append(" ");

            if (i % bytesInLine == 0)
                builder.append("\n");
        }

        return builder.toString();
    }
}
