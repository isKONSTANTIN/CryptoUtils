package su.knrg.crypto.command.commands.seed;

import org.jline.builtins.Completers;
import org.jline.console.impl.ConsoleEngineImpl;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.command.commands.CommandTag;
import su.knrg.crypto.utils.BIP39;
import su.knrg.crypto.utils.args.ArgsTreeBuilder;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

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
                        Arrays.stream(BIP39.ARRAY)
                                .filter(s -> s.contains(line.word()))
                                .map(Candidate::new)
                                .toList()
                );

        for(int i = 0; i < 24; i++)
            builder.addCompleter(completer);

        return builder.parent().build();
    }
}
