package su.knrg.crypto.command.commands.seed;

import org.jline.builtins.Completers;
import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.command.commands.CommandTag;
import su.knrg.crypto.utils.args.ArgsTreeBuilder;
import su.knrg.crypto.utils.worldlists.WordLists;

import java.util.Optional;

public class WordListCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> oMode = args.stringV(0);
        Optional<String> oName = args.stringV(1);

        if (oMode.isEmpty() || !(oMode.get().equals("list") || oMode.get().equals("set")))
            return CommandResult.of("Mode must be 'list' or 'set'", true);

        boolean mode = oMode.map(m -> m.equals("list")).get();

        if (!mode && oName.isEmpty())
            return CommandResult.of("List name not set", true);

        return mode ? list() : setList(oName.get());
    }

    protected CommandResult list() {
        StringBuilder builder = new StringBuilder();

        WordLists.getLists().forEach((l) -> builder.append(l).append("\n"));

        return CommandResult.of(builder.toString());
    }

    protected CommandResult setList(String name) {
        WordLists.WordList newWordList = WordLists.setActiveList(name);

        if (newWordList == null)
            return CommandResult.of("List not found", true);

        return CommandResult.of("Now wordlist is '" + newWordList.getName() + "'");
    }

    @Override
    public String description() {
        return "Change wordlist to working with seed";
    }

    @Override
    public String args() {
        return "list | set <name>";
    }

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .subTree().addPossibleArg("list").parent()
                .recursiveSubTree()
                .addPossibleArg("set")

                .subTree()
                .addPossibleArgs(WordLists.getLists())
                .parent()

                .parent()
                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
