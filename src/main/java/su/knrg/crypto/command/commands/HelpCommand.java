package su.knrg.crypto.command.commands;

import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;

import java.util.List;
import java.util.Map;

public class HelpCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        return run();
    }

    public CommandResult run() {
        StringBuilder builder = new StringBuilder();

        Map<String, Command> commands = handler.getCommands();
        Map<CommandTag, List<String>> commandsNamesByTag = handler.getCommandsNamesByTag();

        for (Map.Entry<CommandTag, List<String>> entry : commandsNamesByTag.entrySet()) {
            builder.append("\n-------- ")
                    .append(entry.getKey().title)
                    .append(" --------")
                    .append("\n");

            for (String alias : entry.getValue()) {
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
        }

        return CommandResult.of(builder.toString());
    }

    @Override
    public String description() {
        return "Show list of commands";
    }

    @Override
    public String args() {
        return null;
    }
}
