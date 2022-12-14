package su.knrg.crypto.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CommandHandler {
    protected HashMap<String, Command> commands = new HashMap<>();
    protected ArrayList<String> commandsNames = new ArrayList<>();

    public CommandResult run(String line) {
        String[] words = line.split(" ");

        Command command = commands.get(words[0]);

        if (command == null)
            return CommandResult.COMMAND_NOT_FOUND;

        return command.run(new ParamsContainer(Arrays.copyOfRange(words, 1, words.length)));
    }

    public boolean registerCommand(String alias, Command command) {
        if (commands.containsKey(alias))
            return false;

        command.init(this);

        commands.put(alias, command);
        commandsNames.add(alias);

        return true;
    }

    public <T extends Command> T getCommand(String alias, Class<T> tClass) {
        return (T) commands.get(alias);
    }

    public String helpPage() {
        StringBuilder builder = new StringBuilder();

        for (String alias : commandsNames) {
            Command command = commands.get(alias);
            String args = command.args();
            String desc = command.description();

            builder.append(alias);

            if (args != null)
                builder.append(" ").append(args);

            builder.append(" - ")
                    .append(desc)
                    .append("\n");
        }


        return builder.toString();
    }
}
