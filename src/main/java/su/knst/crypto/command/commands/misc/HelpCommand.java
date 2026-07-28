package su.knst.crypto.command.commands.misc;

import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;

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
        StringBuilder builder = new StringBuilder();

        Map<String, Command> commands = handler.getCommands();
        Map<CommandTag, List<String>> commandsNamesByTag = handler.getCommandsNamesByTag();

        for (Map.Entry<CommandTag, List<String>> entry : commandsNamesByTag.entrySet()) {
            List<Map.Entry<String, List<String>>> grouped = groupByInstance(commands, entry.getValue());

            if (grouped.isEmpty())
                continue;

            int width = grouped.stream().mapToInt(e -> e.getKey().length()).max().orElse(0);

            builder.append("\n-- ").append(entry.getKey().title).append(" --\n");

            for (Map.Entry<String, List<String>> group : grouped) {
                Command command = commands.get(group.getValue().get(0));

                builder.append("  ")
                        .append(padRight(group.getKey(), width))
                        .append("  ")
                        .append(command.description())
                        .append("\n");
            }
        }

        builder.append("\nType 'help <command>' for detailed usage.\n");

        return CommandResult.of(builder.toString());
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
        builder.append("\n").append(wrap(usage, WRAP_WIDTH, "")).append("\n\n");
        builder.append(wrap(command.description(), WRAP_WIDTH, "  ")).append("\n");

        return CommandResult.of(builder.toString());
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

    private static String padRight(String text, int width) {
        return text + " ".repeat(Math.max(0, width - text.length()));
    }

    private static String wrap(String text, int width, String indent) {
        String[] words = text.split(" ");
        StringBuilder result = new StringBuilder();
        StringBuilder line = new StringBuilder(indent);

        for (String word : words) {
            if (line.length() > indent.length() && line.length() + 1 + word.length() > width) {
                result.append(line).append("\n");
                line = new StringBuilder(indent);
            }

            if (line.length() > indent.length())
                line.append(" ");

            line.append(word);
        }

        result.append(line);

        return result.toString();
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
