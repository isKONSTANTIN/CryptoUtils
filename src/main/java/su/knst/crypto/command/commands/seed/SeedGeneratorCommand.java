package su.knst.crypto.command.commands.seed;

import su.knst.crypto.Main;
import su.knst.crypto.TerminalWorker;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.Panel;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.MnemonicUtils;
import su.knst.crypto.utils.TerminalQuestion;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class SeedGeneratorCommand extends Command {

    @Override
    public CommandResult run(ParamsContainer args) {
        if (args.size() == 0)
            return runInteractive();

        Optional<String> oBase64 = args.stringV(0);

        try {
            return run(Base64.getDecoder().decode(oBase64.orElseThrow()));
        }catch (IllegalArgumentException e) {
            return CommandResult.error("Failed to run: " + e.getMessage());
        }
    }

    private CommandResult runInteractive() {
        TerminalWorker tw = Main.getTerminalWorker();

        Optional<String> oBase64 = tw.ask(new TerminalQuestion(
                "Base64 seed bytes? (leave empty to generate random entropy)", null));

        if (oBase64.isEmpty() || oBase64.get().isBlank())
            return run();

        try {
            return run(Base64.getDecoder().decode(oBase64.get().trim()));
        } catch (IllegalArgumentException e) {
            return CommandResult.error("Failed to run: " + e.getMessage());
        }
    }

    public CommandResult run() {
        byte[] entropy32 = new byte[32];

        SecureRandom random = new SecureRandom();
        random.nextBytes(entropy32);

        return run(entropy32);
    }

    public CommandResult run(byte[] entropy) {
        if (entropy.length < 16) {
            return CommandResult.error(
                    "Not enough source entropy!\n"
                            + "Given: " + entropy.length + " bytes\n"
                            + "Min: 16 bytes"
            );
        }

        String[] mnemonic24 = null;
        String[] mnemonic12 = null;

        try {
            if (entropy.length >= 32)
                mnemonic24 = MnemonicUtils.createMnemonic(Arrays.copyOfRange(entropy, 0, 32));

            mnemonic12 = MnemonicUtils.createMnemonic(Arrays.copyOfRange(entropy, 0, 16));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        List<Panel> panels = new ArrayList<>();

        panels.add(Panel.framed("Source entropy",
                formatBits(entropy, 4) + "\n"
                        + "Base64 encoded: " + Base64.getEncoder().encodeToString(entropy) + "\n"
                        + "Hex encoded: " + HexUtils.bytesToHex(entropy),
                null));

        String inline = joinSeedSections(mnemonic24, mnemonic12, SeedGeneratorCommand::formatMnemonicLine);
        if (!inline.isEmpty())
            panels.add(Panel.framed("Seed phrase", inline, null));

        String vertical = joinSeedSections(mnemonic24, mnemonic12, SeedGeneratorCommand::formatMnemonicList);
        if (!vertical.isEmpty())
            panels.add(Panel.plain(vertical));

        return CommandResult.panels(panels);
    }

    private interface MnemonicFormatter {
        String format(String[] words);
    }

    private static String joinSeedSections(String[] mnemonic24, String[] mnemonic12, MnemonicFormatter formatter) {
        StringBuilder result = new StringBuilder();

        if (mnemonic12 != null) {
            if (!result.isEmpty())
                result.append("\n\n");

            result.append("12-word seed:\n").append(formatter.format(mnemonic12));
        }

        if (mnemonic24 != null)
            result.append("\n24-word seed:\n").append(formatter.format(mnemonic24));

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

    @Override
    public String description() {
        return "Generate a fresh random 12/24-word seed phrase, or restore one from base64-encoded entropy";
    }

    @Override
    public String args() {
        return "[base64 string]";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
