package su.knst.crypto.command.commands.seed;

import su.knst.crypto.Main;
import su.knst.crypto.command.ArgSource;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.CommandResultBuilder;
import su.knst.crypto.command.InteractiveArgSource;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.ScriptedArgSource;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.MnemonicUtils;
import su.knst.crypto.utils.exceptions.WrongMnemonicException;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;

import static su.knst.crypto.command.commands.seed.SeedGeneratorCommand.formatBits;
import static su.knst.crypto.utils.MnemonicUtils.*;

public class SeedExtenderCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        ArgSource in = args.size() == 0
                ? new InteractiveArgSource(Main.getTerminalWorker())
                : new ScriptedArgSource(args);

        Optional<String[]> oWords = in.words("12 seed words separated by spaces?");

        if (oWords.isEmpty())
            return CommandResult.error("No input");

        if (oWords.get().length != 12)
            return CommandResult.error("Wrong mnemonic size");

        return run(oWords.get());
    }

    private CommandResult run(String[] mnemonic) {
        try {
            checkMnemonic(mnemonic);
        } catch (WrongMnemonicException | NoSuchAlgorithmException e) {
            return CommandResult.of("Failed to check mnemonic: " + e.getMessage());
        }

        byte[] entropy = entropyFromMnemonic(mnemonic);

        CommandResultBuilder resultBuilder = CommandResultBuilder.builder();

        resultBuilder
                .line("Source entropy:")
                .line(formatBits(entropy, 4));

        byte[] hash;
        try {
            hash = MnemonicUtils.sha256(entropy);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        entropy = Arrays.copyOf(entropy, 32);

        System.arraycopy(hash, 0, entropy, 16, 16);

        resultBuilder.line()
                .line("Extended entropy:")
                .line(formatBits(entropy, 4))
                .line();

        CommandResult seed = this.handler
                .getCommand("seed", SeedGeneratorCommand.class)
                .orElseThrow()
                .run(entropy);

        return resultBuilder.merge(seed).build();
    }

    @Override
    public String description() {
        return "Extend a 12-word seed phrase to a 24-word one by putting a checksum hash into the entropy";
    }

    @Override
    public String args() {
        return "<word_1> <word_2> ...";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
