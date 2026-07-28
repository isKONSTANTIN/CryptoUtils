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
import su.knst.crypto.utils.exceptions.WrongMnemonicException;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

import static su.knst.crypto.command.commands.seed.SeedGeneratorCommand.formatBits;
import static su.knst.crypto.utils.MnemonicUtils.*;

public class SeedToBaseCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        ArgSource in = args.size() == 0
                ? new InteractiveArgSource(Main.getTerminalWorker())
                : new ScriptedArgSource(args);

        Optional<String[]> oWords = in.words("Seed words separated by spaces?");

        if (oWords.isEmpty())
            return CommandResult.error("No input");

        return run(oWords.get());
    }

    private CommandResult run(String[] mnemonic) {
        try {
            checkMnemonic(mnemonic);
        } catch (WrongMnemonicException | NoSuchAlgorithmException e) {
            return CommandResult.error("Failed to check mnemonic: " + e.getMessage());
        }

        byte[] entropy = entropyFromMnemonic(mnemonic);

        CommandResultBuilder builder = CommandResultBuilder.builder();

        builder.line("Source entropy:")
                .line(formatBits(entropy, 4))
                .line()
                .line("Base64 encoded: " + Base64.getEncoder().encodeToString(entropy));

        return builder.build();
    }

    @Override
    public String description() {
        return "Transform a seed phrase into its raw base64 entropy (without the checksum word)";
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
