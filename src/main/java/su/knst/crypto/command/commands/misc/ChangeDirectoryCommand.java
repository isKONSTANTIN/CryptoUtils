package su.knst.crypto.command.commands.misc;

import org.jline.builtins.Completers;
import su.knst.crypto.Main;
import su.knst.crypto.cli.Ask;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.LineCommand;
import su.knst.crypto.utils.args.ArgsTreeBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public class ChangeDirectoryCommand extends LineCommand {
    @Override
    public CommandResult run(Ask in, String argument) {
        Optional<String> target = argument != null
                ? Optional.of(argument.strip())
                : in.stringWithFileCompletion("Directory?");

        if (target.isEmpty())
            return CommandResult.VOID;

        Main.changeCurrentPath(target.get());

        return listCurrentDirectory();
    }

    /** Listed here rather than by shelling out to `ls`, which isn't present on every host. */
    private static CommandResult listCurrentDirectory() {
        Path directory = Main.getCurrentPath();

        try (Stream<Path> entries = Files.list(directory)) {
            String listing = entries
                    .sorted()
                    .map(path -> Files.isDirectory(path) ? path.getFileName() + "/" : path.getFileName().toString())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("(empty)");

            return CommandResult.of(directory + "\n\n" + listing);
        } catch (IOException e) {
            return CommandResult.error("Failed to list " + directory + ": " + e.getMessage());
        }
    }

    @Override
    public String description() {
        return "Change current directory";
    }

    @Override
    public Completers.TreeCompleter.Node completerNode(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .addCompleter(new Completers.DirectoriesCompleter(Main::getCurrentPath))

                .build();
    }
}
