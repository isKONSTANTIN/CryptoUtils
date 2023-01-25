package su.knrg.crypto;

import su.knrg.crypto.command.CommandHandler;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.commands.*;
import su.knrg.crypto.command.commands.keys.ECDHEKeyGeneratorCommand;
import su.knrg.crypto.command.commands.keys.RSAKeyGeneratorCommand;
import su.knrg.crypto.command.commands.qr.QRCodeCommand;
import su.knrg.crypto.command.commands.seed.SeedECDHECipherCommand;
import su.knrg.crypto.command.commands.seed.SeedGeneratorCommand;
import su.knrg.crypto.command.commands.seed.SeedRSACipherCommand;

import java.util.Scanner;

public class Main {
    protected CommandHandler handler = new CommandHandler();
    protected static boolean run = true;

    Main() {
        handler.registerCommand("help", new HelpCommand());
        handler.registerCommand("exit", new ExitCommand());
        handler.registerCommand("seed", new SeedGeneratorCommand());
        handler.registerCommand("rsa_key", new RSAKeyGeneratorCommand());
        handler.registerCommand("ecdhe_key", new ECDHEKeyGeneratorCommand());
        handler.registerCommand("seed_rsa_cipher", new SeedRSACipherCommand());
        handler.registerCommand("seed_ecdhe_cipher", new SeedECDHECipherCommand());
        handler.registerCommand("qr", new QRCodeCommand());
    }

    public static void shutdown() {
        run = false;
    }

    void start() {
        Scanner scanner = new Scanner(System.in);

        CommandResult preResult = handler.run("help");

        (preResult.error ? System.err : System.out).println(preResult.message);

        while (run) {
            System.out.print("cu> ");
            String line = scanner.nextLine();

            CommandResult result = handler.run(line);

            System.out.println(result.message);
        }
    }

    public static void main(String[] args) {
        new Main().start();
    }
}
