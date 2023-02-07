package su.knst.crypto.command;

import su.knst.crypto.command.commands.CommandTag;

import java.io.IOException;
import java.util.*;

import static su.knst.crypto.utils.sys.SystemCommandsBridge.runSystemCommand;

public class CommandHandler {
    protected final HashMap<String, Command> commands = new HashMap<>();
    protected final ArrayList<String> commandsNames = new ArrayList<>();
    protected final LinkedHashMap<CommandTag, ArrayList<String>> commandsNamesByTag = new LinkedHashMap<>();

    public CommandResult run(String line) {
        String[] words = line.split(" ");

        Command command = commands.get(words[0]);

        if (command == null) {
            try {
                runSystemCommand(words);
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.COMMAND_NOT_FOUND;
            }

            return CommandResult.of("");
        }

        return command.run(new ParamsContainer(Arrays.copyOfRange(words, 1, words.length)));
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
