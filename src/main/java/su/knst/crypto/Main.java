package su.knst.crypto;

import su.knst.crypto.command.CommandHandler;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.command.commands.backup.BackupCreateCommand;
import su.knst.crypto.command.commands.backup.BackupRestoreCommand;
import su.knst.crypto.command.commands.misc.ChangeDirectoryCommand;
import su.knst.crypto.command.commands.misc.DeleteCommand;
import su.knst.crypto.command.commands.misc.ExitCommand;
import su.knst.crypto.command.commands.misc.HelpCommand;
import su.knst.crypto.command.commands.seed.SeedCommand;
import su.knst.crypto.utils.NativeAwtLibraries;
import su.knst.crypto.utils.worldlists.WordLists;

import java.io.IOException;
import java.nio.file.Path;

public class Main {
    protected final CommandHandler handler = new CommandHandler();
    protected static TerminalWorker terminalWorker;
    protected static Path currentPath = Path.of("./").toAbsolutePath();

    public static String getVersion() {
        String version = Main.class.getPackage().getImplementationVersion();

        return version != null ? version : "dev";
    }

    public Main() {
        WordLists.preload();

        handler.addTagManually(CommandTag.MISC);
        handler.addTagManually(CommandTag.CRYPTOCURRENCIES);
        handler.addTagManually(CommandTag.BACKUPS);

        ExitCommand exitCommand = new ExitCommand();

        handler.registerCommand("help", new HelpCommand());
        handler.registerCommand("exit", exitCommand);
        handler.registerCommand("q", exitCommand);
        handler.registerCommand("cd", new ChangeDirectoryCommand());
        handler.registerCommand("delete", new DeleteCommand());

        handler.registerCommand("seed", new SeedCommand());

        handler.registerCommand("backup", new BackupCreateCommand());
        handler.registerCommand("restore", new BackupRestoreCommand());

        terminalWorker = new TerminalWorker(handler);
    }

    public static void shutdown() {
        terminalWorker.stop();
    }

    public CommandHandler getHandler() {
        return handler;
    }

    public static TerminalWorker getTerminalWorker() {
        return terminalWorker;
    }

    public static Path getCurrentPath() {
        return currentPath;
    }

    public static void changeCurrentPath(String add) {
        Path newPath = currentPath.resolve(add);
        if (!newPath.toFile().exists())
            return;

        currentPath = newPath;
    }

    public void start() {
        try {
            terminalWorker.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * CryptoUtils is interactive: every command asks its own questions, so there is no argument
     * grammar to invoke one with. Only --version is answered without starting the REPL.
     */
    public static void main(String[] args) {
        if (args.length == 1 && (args[0].equals("--version") || args[0].equals("-v"))) {
            System.out.println(getVersion());
            return;
        }

        if (args.length > 0) {
            System.err.println("CryptoUtils is interactive - run it without arguments.");
            System.exit(2);
        }

        NativeAwtLibraries.extractAndRegister();

        new Main().start();
    }
}
