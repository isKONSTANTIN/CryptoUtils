package su.knst.crypto.command;

import su.knst.crypto.command.commands.CommandTag;

import java.io.IOException;
import java.util.*;

import static su.knst.crypto.utils.sys.SystemCommandsBridge.runSystemCommand;

public class CommandHandler {
    protected HashMap<String, Command> commands = new HashMap<>();
    protected ArrayList<String> commandsNames = new ArrayList<>();
    protected LinkedHashMap<CommandTag, ArrayList<String>> commandsNamesByTag = new LinkedHashMap<>();

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

    public boolean registerCommand(String alias, Command command) {
        if (commands.containsKey(alias))
            return false;

        command.init(this);

        commands.put(alias, command);
        commandsNames.add(alias);
        addToTagMap(command.tag(), alias);

        return true;
    }

    protected void addToTagMap(CommandTag tag, String alias) {
        addTagManually(tag);
        commandsNamesByTag.get(tag).add(alias);
    }

    public void addTagManually(CommandTag tag) {
        if (!commandsNamesByTag.containsKey(tag))
            commandsNamesByTag.put(tag, new ArrayList<>());
    }

    public <T extends Command> T getCommand(String alias, Class<T> tClass) {
        return (T) commands.get(alias);
    }

    public Map<String, Command> getCommands() {
        return Collections.unmodifiableMap(commands);
    }

    public List<String> getCommandsNames() {
        return Collections.unmodifiableList(commandsNames);
    }

    public Map<CommandTag, List<String>> getCommandsNamesByTag() {
        return Collections.unmodifiableMap(commandsNamesByTag);
    }
}
