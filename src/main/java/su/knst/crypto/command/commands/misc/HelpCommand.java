package su.knst.crypto.command.commands.misc;

import org.jline.utils.AttributedStyle;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.Panel;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.ConsoleBox;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class HelpCommand extends Command {
    private static final int WRAP_WIDTH = 80;

    @Override
    public CommandResult run(ParamsContainer args) {
        return args.stringV(0)
                .map(this::runDetailed)
                .orElseGet(this::runList);
    }

    public CommandResult run() {
        return runList();
    }

    private CommandResult runList() {
        List<Panel> panels = new ArrayList<>();

        Map<String, Command> commands = handler.getCommands();
        Map<CommandTag, List<String>> commandsNamesByTag = handler.getCommandsNamesByTag();

        for (Map.Entry<CommandTag, List<String>> entry : commandsNamesByTag.entrySet()) {
            List<Map.Entry<String, List<String>>> grouped = groupByInstance(commands, entry.getValue());

            if (grouped.isEmpty())
                continue;

            int nameWidth = grouped.stream().mapToInt(e -> e.getKey().length()).max().orElse(0);

            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, List<String>> group : grouped) {
                Command command = commands.get(group.getValue().get(0));

                lines.add(ConsoleBox.padRight(group.getKey(), nameWidth) + "  " + command.description());
            }

            panels.add(Panel.framed(entry.getKey().title, String.join("\n", lines), colorFor(entry.getKey())));
        }

        panels.add(Panel.plain("Type 'help <command>' for detailed usage."));

        return CommandResult.panels(panels);
    }

    private CommandResult runDetailed(String alias) {
        Map<String, Command> commands = handler.getCommands();
        Command command = commands.get(alias);

        if (command == null)
            return CommandResult.error("Unknown command: " + alias);

        List<String> aliases = new ArrayList<>();
        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            if (entry.getValue() == command)
                aliases.add(entry.getKey());
        }

        String usage = String.join(", ", aliases);
        String args = command.args();
        if (args != null)
            usage += " " + args;

        StringBuilder builder = new StringBuilder();
        builder.append(ConsoleBox.wrap(usage, WRAP_WIDTH, "")).append("\n\n");
        builder.append(ConsoleBox.wrap(command.description(), WRAP_WIDTH, "  "));

        return CommandResult.plain(builder.toString());
    }

    // Groups aliases that resolve to the very same Command instance, preserving registration order.
    private List<Map.Entry<String, List<String>>> groupByInstance(Map<String, Command> commands, List<String> aliasesInTag) {
        List<List<String>> order = new ArrayList<>();
        IdentityHashMap<Command, List<String>> grouped = new IdentityHashMap<>();

        for (String alias : aliasesInTag) {
            Command command = commands.get(alias);
            List<String> aliasGroup = grouped.get(command);

            if (aliasGroup == null) {
                aliasGroup = new ArrayList<>();
                grouped.put(command, aliasGroup);
                order.add(aliasGroup);
            }

            aliasGroup.add(alias);
        }

        List<Map.Entry<String, List<String>>> result = new ArrayList<>();
        for (List<String> aliasGroup : order)
            result.add(Map.entry(String.join(", ", aliasGroup), aliasGroup));

        return result;
    }

    private static AttributedStyle colorFor(CommandTag tag) {
        return switch (tag) {
            case MISC -> AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE);
            case BACKUPS -> AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE).bold();
            case CRYPTOGRAPHY -> AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold();
            case CRYPTOCURRENCIES -> AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).bold();
        };
    }

    @Override
    public String description() {
        return "Show list of commands";
    }

    @Override
    public String args() {
        return "[command]";
    }
}
