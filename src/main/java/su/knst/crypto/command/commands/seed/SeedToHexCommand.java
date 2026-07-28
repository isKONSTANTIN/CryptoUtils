package su.knst.crypto.command.commands.seed;

import su.knst.crypto.Main;
import su.knst.crypto.TerminalWorker;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.CommandResultBuilder;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.exceptions.WrongMnemonicException;

import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static su.knst.crypto.command.commands.seed.SeedGeneratorCommand.formatBits;
import static su.knst.crypto.utils.MnemonicUtils.*;

public class SeedToHexCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        if (args.size() == 0)
            return runInteractive();

        String[] mnemonic = new String[args.size()];

        for (int i = 0; i < mnemonic.length; i++) {
            mnemonic[i] = args.stringV(i).orElseThrow();
        }

        return run(mnemonic);
    }

    private CommandResult runInteractive() {
        Optional<String[]> oWords = Prompts.askWords(Main.getTerminalWorker(), "Seed words separated by spaces?");

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
                .line("Hex encoded: " + HexUtils.bytesToHex(entropy));

        return builder.build();
    }

    @Override
    public String description() {
        return "Transform a seed phrase into its raw hex entropy (without the checksum word)";
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
