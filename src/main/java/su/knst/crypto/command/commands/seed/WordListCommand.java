package su.knst.crypto.command.commands.seed;

import su.knst.crypto.Main;
import su.knst.crypto.TerminalWorker;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.worldlists.WordLists;

import java.util.List;
import java.util.Optional;

public class WordListCommand extends Command {
    private static final List<Prompts.Choice> MODE_CHOICES = List.of(
            new Prompts.Choice("list", "List", "Show available wordlists"),
            new Prompts.Choice("set", "Set", "Change the active wordlist")
    );

    @Override
    public CommandResult run(ParamsContainer args) {
        if (args.size() == 0)
            return runInteractive();

        Optional<String> oMode = args.stringV(0);
        Optional<String> oName = args.stringV(1);

        if (oMode.isEmpty() || !(oMode.get().equals("list") || oMode.get().equals("set")))
            return CommandResult.error("Mode must be 'list' or 'set'");

        boolean mode = oMode.map(m -> m.equals("list")).get();

        if (!mode && oName.isEmpty())
            return CommandResult.error("List name not set");

        return mode ? list() : setList(oName.get());
    }

    private CommandResult runInteractive() {
        TerminalWorker tw = Main.getTerminalWorker();

        Optional<String> oMode = Prompts.askChoice(tw, "List available wordlists, or set the active one?", MODE_CHOICES);

        if (oMode.isEmpty())
            return CommandResult.error("No input");

        if (oMode.get().equals("list"))
            return list();

        List<Prompts.Choice> nameChoices = WordLists.getLists().stream()
                .map((name) -> new Prompts.Choice(name, name))
                .toList();

        Optional<String> oName = Prompts.askChoice(tw, "Which wordlist?", nameChoices);

        if (oName.isEmpty())
            return CommandResult.error("No input");

        return setList(oName.get());
    }

    protected CommandResult list() {
        StringBuilder builder = new StringBuilder();

        WordLists.getLists().forEach((l) -> builder.append(l).append("\n"));

        return CommandResult.of(builder.toString());
    }

    protected CommandResult setList(String name) {
        WordLists.WordList newWordList = WordLists.setActiveList(name);

        if (newWordList == null)
            return CommandResult.error("List not found");

        return CommandResult.of("Now wordlist is '" + newWordList.name() + "'");
    }

    @Override
    public String description() {
        return "List available BIP-39 wordlists or change the active one used to build/check seed phrases";
    }

    @Override
    public String args() {
        return "list | set <name>";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
