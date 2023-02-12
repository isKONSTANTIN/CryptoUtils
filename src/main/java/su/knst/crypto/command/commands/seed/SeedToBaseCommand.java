package su.knst.crypto.command.commands.seed;

import org.jline.builtins.Completers;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.CommandResultBuilder;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.args.ArgsTreeBuilder;
import su.knst.crypto.utils.exceptions.WrongMnemonicException;
import su.knst.crypto.utils.worldlists.WordLists;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

import static su.knst.crypto.command.commands.seed.SeedGeneratorCommand.formatBits;
import static su.knst.crypto.utils.MnemonicUtils.*;

public class SeedToBaseCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        String[] mnemonic = new String[args.size()];

        for (int i = 0; i < mnemonic.length; i++) {
            mnemonic[i] = args.stringV(i).orElseThrow();
        }

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
        return "Transform seed words to base64 entropy without checksum";
    }

    @Override
    public String args() {
        return "<word_1> <word_2> ...";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
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

        for(int i = 0; i < 24; i++)
            builder.addCompleter(completer);

        return builder.parent().build();
    }
}
