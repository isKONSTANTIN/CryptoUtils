package su.knrg.crypto;

import su.knrg.crypto.command.CommandHandler;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.commands.*;
import su.knrg.crypto.command.commands.hex.HexCommand;
import su.knrg.crypto.command.commands.keys.ECDHEKeyGeneratorCommand;
import su.knrg.crypto.command.commands.keys.RSAKeyGeneratorCommand;
import su.knrg.crypto.command.commands.qr.CodeCommand;
import su.knrg.crypto.command.commands.qr.ErrorCorrectionLevelsCommand;
import su.knrg.crypto.command.commands.seed.SeedECDHECipherCommand;
import su.knrg.crypto.command.commands.seed.SeedGeneratorCommand;
import su.knrg.crypto.command.commands.seed.SeedRSACipherCommand;
import su.knrg.crypto.command.commands.shamir.ShamirCommand;
import su.knrg.crypto.utils.codes.SimplePDF417Worker;
import su.knrg.crypto.utils.codes.SimpleQRCodeWorker;

import java.io.IOException;
import java.util.Scanner;

public class Main {
    protected CommandHandler handler = new CommandHandler();
    protected static TerminalWorker terminalWorker;

    Main() {
        handler.addTagManually(CommandTag.MISC);
        handler.addTagManually(CommandTag.CRYPTOCURRENCIES);
        handler.addTagManually(CommandTag.CRYPTOGRAPHY);
        handler.addTagManually(CommandTag.BACKUPS);

        handler.registerCommand("help", new HelpCommand());
        handler.registerCommand("exit", new ExitCommand());
        handler.registerCommand("seed", new SeedGeneratorCommand());
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

    void start() {
        try {
            terminalWorker.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //CommandResult preResult = handler.run("help");

        //(preResult.error ? System.err : System.out).println(preResult.message);

        try {
            terminalWorker.start();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    public static void main(String[] args) {
        new Main().start();
    }
}
