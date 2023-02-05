package su.knst.crypto;

import su.knst.crypto.command.CommandHandler;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.command.commands.misc.ChangeDirectoryCommand;
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
import su.knst.crypto.utils.worldlists.WordLists;

import java.io.IOException;
import java.nio.file.Path;

public class Main {
    protected final CommandHandler handler = new CommandHandler();
    protected static TerminalWorker terminalWorker;
    protected static Path currentPath = Path.of("./").toAbsolutePath();

    Main() {
        WordLists.preload();

        handler.addTagManually(CommandTag.MISC);
        handler.addTagManually(CommandTag.CRYPTOCURRENCIES);
        handler.addTagManually(CommandTag.CRYPTOGRAPHY);
        handler.addTagManually(CommandTag.BACKUPS);

        handler.registerCommand("help", new HelpCommand());
        handler.registerCommand("exit", new ExitCommand());
        handler.registerCommand("cd", new ChangeDirectoryCommand());
        handler.registerCommand("q", new ExitCommand());

        handler.registerCommand("seed", new SeedGeneratorCommand());
        handler.registerCommand("seed_to_base", new SeedToBaseCommand());
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

        terminalWorker = new TerminalWorker(handler);
    }

    public static void shutdown() {
        terminalWorker.stop();
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

    void start() {
        try {
            terminalWorker.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            terminalWorker.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new Main().start();
    }
}
