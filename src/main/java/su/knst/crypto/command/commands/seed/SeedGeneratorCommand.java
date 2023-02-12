package su.knst.crypto.command.commands.seed;

import org.jline.builtins.Completers;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.CommandResultBuilder;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.MnemonicUtils;
import su.knst.crypto.utils.args.ArgsTreeBuilder;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

public class SeedGeneratorCommand extends Command {

    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> oBase64 = args.stringV(0);

        try {
            return oBase64.isPresent() ? run(Base64.getDecoder().decode(oBase64.get())) : run();
        }catch (IllegalArgumentException e) {
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
        CommandResultBuilder resultBuilder = CommandResultBuilder.builder();

        resultBuilder
                .line("Source entropy:").line()
                .line(formatBits(entropy, 4))
                .line("Base64 encoded: " + Base64.getEncoder().encodeToString(entropy));

        if (entropy.length >= 32) {
            try {
                resultBuilder
                        .line()
                        .line("24-word seed:")
                        .line(formatMnemonic(
                                MnemonicUtils.createMnemonic(Arrays.copyOfRange(entropy, 0, 32))
                        ));
            }catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        if (entropy.length >= 16) {
            try {
                resultBuilder
                        .line()
                        .line("12-word seed:")
                        .line(formatMnemonic(
                                MnemonicUtils.createMnemonic(Arrays.copyOfRange(entropy, 0, 16))
                        ));
            }catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        if (entropy.length < 16) {
            resultBuilder.error()
                    .line()
                    .line("Not enough source entropy!")
                    .line("Given: " + entropy.length + " bytes")
                    .line("Min: 16 bytes");
        }

        return resultBuilder.build();
    }

    public static String formatMnemonic(String[] words) {
        StringBuilder list = new StringBuilder();
        StringBuilder line = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            list
                    .append(i + 1)
                    .append(". ")
                    .append(i + 1 < 10 ? " " : "")
                    .append(words[i])
                    .append("\n");

            line.append(words[i]).append(" ");
        }

        return list.append("\n").append(line).toString();
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
        return "Generate or restore 12 and 24 seed phrase";
    }

    @Override
    public String args() {
        return "[base64 string]";
    }

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .addTip("[base64 string]", "Seed bytes in base64")

                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
