package su.knst.crypto;

import su.knst.crypto.command.CommandHandler;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.command.commands.backup.BackupCreateCommand;
import su.knst.crypto.command.commands.backup.BackupRestoreCommand;
import su.knst.crypto.command.commands.misc.ChangeDirectoryCommand;
import su.knst.crypto.command.commands.misc.DeleteCommand;
import su.knst.crypto.command.commands.misc.ExitCommand;
import su.knst.crypto.command.commands.misc.HelpCommand;
import su.knst.crypto.command.commands.hex.HexCommand;
import su.knst.crypto.command.commands.keys.ECDHEKeyGeneratorCommand;
import su.knst.crypto.command.commands.keys.RSAKeyGeneratorCommand;
import su.knst.crypto.command.commands.qr.CodeCommand;
import su.knst.crypto.command.commands.qr.ErrorCorrectionLevelsCommand;
import su.knst.crypto.command.commands.seed.*;
import su.knst.crypto.command.commands.shamir.ShamirCommand;
import su.knst.crypto.utils.codes.SimplePDF417Worker;
import su.knst.crypto.utils.codes.SimpleQRCodeWorker;
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
        handler.addTagManually(CommandTag.CRYPTOGRAPHY);
        handler.addTagManually(CommandTag.BACKUPS);

        ExitCommand exitCommand = new ExitCommand();

        handler.registerCommand("help", new HelpCommand());
        handler.registerCommand("exit", exitCommand);
        handler.registerCommand("cd", new ChangeDirectoryCommand());
        handler.registerCommand("delete", new DeleteCommand());
        handler.registerCommand("q", exitCommand);

        handler.registerCommand("seed", new SeedGeneratorCommand());
        handler.registerCommand("seed_to_base", new SeedToBaseCommand());
        handler.registerCommand("seed_to_hex", new SeedToHexCommand());
        handler.registerCommand("hex_to_seed", new HexToSeedCommand());
        handler.registerCommand("extend_seed", new SeedExtenderCommand());
        handler.registerCommand("wordlist", new WordListCommand());

        handler.registerCommand("rsa_key", new RSAKeyGeneratorCommand());
        handler.registerCommand("ecdhe_key", new ECDHEKeyGeneratorCommand());
        handler.registerCommand("seed_rsa_cipher", new SeedRSACipherCommand());
        handler.registerCommand("seed_ecdhe_cipher", new SeedECDHECipherCommand());

        handler.registerCommand("qr", new CodeCommand(new SimpleQRCodeWorker()));
        handler.registerCommand("pdf417", new CodeCommand(new SimplePDF417Worker()));
        handler.registerCommand("ecl", new ErrorCorrectionLevelsCommand());
        handler.registerCommand("shamir", new ShamirCommand());
        handler.registerCommand("hex", new HexCommand());

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

    public static void main(String[] args) {
        NativeAwtLibraries.extractAndRegister();

        Main main = new Main();

        if (args.length == 0) {
            main.start();
            return;
        }

        CommandResult result = main.getHandler().run(args);

        System.out.println(result.message());
        System.exit(result.error() ? 1 : 0);
    }
}
