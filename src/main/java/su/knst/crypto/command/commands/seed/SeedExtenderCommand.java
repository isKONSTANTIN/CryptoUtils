package su.knst.crypto.command.commands.seed;

import org.jline.builtins.Completers;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.CommandResultBuilder;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.MnemonicUtils;
import su.knst.crypto.utils.args.ArgsTreeBuilder;
import su.knst.crypto.utils.exceptions.WrongMnemonicException;
import su.knst.crypto.utils.worldlists.WordLists;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static su.knst.crypto.command.commands.seed.SeedGeneratorCommand.formatBits;
import static su.knst.crypto.utils.MnemonicUtils.*;

public class SeedExtenderCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {

        if (args.size() != 12)
            return CommandResult.error("Wrong mnemonic size");

        String[] mnemonic = new String[12];

        for (int i = 0; i < args.size(); i++)
            mnemonic[i] = args.stringV(i).orElseThrow();

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
        return "Extend 12-word to 24-word seed by putting hash sum at entropy";
    }

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        ArgsTreeBuilder builder = ArgsTreeBuilder.builder().addPossibleArg(alias)
                .recursiveSubTree();

        Completer completer = (reader, line, candidates) ->
                candidates.addAll(
                        Arrays.stream(WordLists.getActiveList().array())
                                .filter(s -> s.contains(line.word()))
                                .map(Candidate::new)
                                .toList()
                );

        for(int i = 0; i < 12; i++)
            builder.addCompleter(completer);

        return builder.parent().build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }

    @Override
    public String args() {
        return "<word_1> <word_2> ...";
    }
}
