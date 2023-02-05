package su.knrg.crypto.command.commands.seed;

import org.jline.builtins.Completers;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.command.commands.CommandTag;
import su.knrg.crypto.utils.args.ArgsTreeBuilder;
import su.knrg.crypto.utils.worldlists.WordLists;

import java.util.Arrays;
import java.util.Base64;

import static su.knrg.crypto.command.commands.seed.SeedGeneratorCommand.printBits;
import static su.knrg.crypto.utils.MnemonicGenerator.fromMnemonic;

public class SeedToBaseCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        if (args.size() != 24)
            return CommandResult.of("Wrong mnemonic size");

        String[] mnemonic = new String[24];

        for (int i = 0; i < args.size(); i++) {
            mnemonic[i] = args.stringV(i).get();
        }

        WordLists.WordList wordList = WordLists.getActiveList();

        for (String word : mnemonic) {
            if (wordList.getIndex(word).isEmpty())
                return CommandResult.of("'" + word + "' not found in " + wordList.getName() + " list");
        }

        byte[] entropy = fromMnemonic(mnemonic);

        System.out.println("\nsource entropy:");
        printBits(entropy, 4);
        System.out.println("base-64 encoded: " + Base64.getEncoder().encodeToString(entropy));

        return CommandResult.VOID;
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
                        Arrays.stream(WordLists.getActiveList().getArray())
                                .filter(s -> s.contains(line.word()))
                                .map(Candidate::new)
                                .toList()
                );

        for(int i = 0; i < 24; i++)
            builder.addCompleter(completer);

        return builder.parent().build();
    }
}
