package su.knst.crypto.command.commands.seed;

import org.jline.builtins.Completers;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
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
            return CommandResult.of("Failed to run: " + e.getMessage());
        }
    }

    public CommandResult run() {
        byte[] entropy32 = new byte[32];

        SecureRandom random = new SecureRandom();
        random.nextBytes(entropy32);

        return run(entropy32);
    }

    @SuppressWarnings("SameReturnValue")
    public CommandResult run(byte[] entropy) {
        System.out.println("Source entropy:");
        printBits(entropy, 4);
        System.out.println("\nBase64 encoded: " + Base64.getEncoder().encodeToString(entropy));

        if (entropy.length >= 32) {
            try {
                System.out.println("\n24-word seed:");
                printSeed(MnemonicUtils.createMnemonic(Arrays.copyOfRange(entropy, 0, 32)));
            }catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        if (entropy.length >= 16) {
            try {
                System.out.println("\n12-word seed:");
                printSeed(MnemonicUtils.createMnemonic(Arrays.copyOfRange(entropy, 0, 16)));
            }catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        if (entropy.length < 16) {
            System.out.println("Not enough source entropy!");
            System.out.println("Given: " + entropy.length + " bytes");
            System.out.println("Min: 16 bytes");
        }

        return CommandResult.VOID;
    }

    public static void printSeed(String[] words) {
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

        System.out.println(list);
        System.out.println(line);
    }

    public static void printBits(byte[] bytes, int gap) {
        int i = 1;

        for (byte b : bytes) {
            System.out.print(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0') + " ");

            if (i % gap == 0)
                System.out.print("\n");

            i++;
        }
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
