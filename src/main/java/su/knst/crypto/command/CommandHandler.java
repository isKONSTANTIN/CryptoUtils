package su.knst.crypto.command;

import su.knst.crypto.cli.Ask;
import su.knst.crypto.cli.Questioner;
import su.knst.crypto.command.commands.CommandTag;

import java.io.IOException;
import java.util.*;

import static su.knst.crypto.utils.sys.SystemCommandsBridge.runSystemCommand;

public class CommandHandler {
    protected final HashMap<String, Command> commands = new HashMap<>();
    protected final ArrayList<String> commandsNames = new ArrayList<>();
    protected final LinkedHashMap<CommandTag, ArrayList<String>> commandsNamesByTag = new LinkedHashMap<>();

    protected Questioner questioner = question -> Optional.empty();

    public void setQuestioner(Questioner questioner) {
        this.questioner = questioner;
    }

    public CommandResult run(String line) {
        String trimmed = line.strip();

        if (trimmed.isEmpty())
            return CommandResult.VOID;

        String[] parts = trimmed.split("\\s+", 2);
        String name = parts[0];
        String argument = parts.length > 1 ? parts[1] : null;

        Command command = commands.get(name);

        if (command == null) {
            try {
                runSystemCommand(trimmed.split("\\s+"));
            } catch (IOException e) {
                return CommandResult.COMMAND_NOT_FOUND;
            }

            return CommandResult.VOID;
        }

        Ask in = new Ask(questioner);

        if (command instanceof LineCommand lineCommand)
            return lineCommand.run(in, argument).withTitle(name);

        CommandResult result = command.run(in).withTitle(name);

        // typed-out arguments used to be dropped without a word here; commands ask their own
        // questions now, so say so rather than leave the user wondering where the text went
        return argument == null ? result : result.withNotice("ignored: " + argument);
    }

    public <T extends Command> void registerCommand(String alias, T command) {
        if (commands.containsKey(alias))
            return;

        command.init(this);

        commands.put(alias, command);
        commandsNames.add(alias);
        addToTagMap(command.tag(), alias);
    }

    protected void addToTagMap(CommandTag tag, String alias) {
        addTagManually(tag);
        commandsNamesByTag.get(tag).add(alias);
    }

    public void addTagManually(CommandTag tag) {
        if (!commandsNamesByTag.containsKey(tag))
            commandsNamesByTag.put(tag, new ArrayList<>());
    }

    public <T extends Command> Optional<T> getCommand(String alias, Class<T> tClass) {
        Command command = commands.get(alias);

        if (!tClass.isInstance(command))
            return Optional.empty();

        return Optional.of(tClass.cast(command));
    }

    public <T extends Command> Optional<T> getCommand(Class<T> tClass) {
        return commands.values().stream()
                .filter(tClass::isInstance)
                .map(tClass::cast)
                .findFirst();
    }

    public Map<String, Command> getCommands() {
        return Collections.unmodifiableMap(commands);
    }

    @SuppressWarnings("unused")
    public List<String> getCommandsNames() {
        return Collections.unmodifiableList(commandsNames);
    }

    public Map<CommandTag, List<String>> getCommandsNamesByTag() {
        return Collections.unmodifiableMap(commandsNamesByTag);
    }
}
