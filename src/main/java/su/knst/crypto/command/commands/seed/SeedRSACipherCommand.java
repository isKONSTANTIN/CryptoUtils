package su.knst.crypto.command.commands.seed;

import su.knst.crypto.Main;
import su.knst.crypto.command.ArgSource;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.InteractiveArgSource;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.ScriptedArgSource;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.SimpleRSA;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class SeedRSACipherCommand extends Command {
    private static final List<Prompts.Choice> MODE_CHOICES = List.of(
            new Prompts.Choice("encrypt", "Encrypt", "Encrypt entropy with an RSA public key"),
            new Prompts.Choice("decrypt", "Decrypt", "Decrypt entropy with an RSA private key"),
            new Prompts.Choice("decrypt_old", "Decrypt (legacy)", "Decrypt entropy encrypted with the old PKCS#1 v1.5 padding")
    );

    @Override
    public CommandResult run(ParamsContainer args) {
        ArgSource in = args.size() == 0
                ? new InteractiveArgSource(Main.getTerminalWorker())
                : new ScriptedArgSource(args);

        return resolve(in);
    }

    // Argument order (mode, entropy, key) matches args() and is relied on by scripted callers.
    private CommandResult resolve(ArgSource in) {
        Optional<String> oMode = in.choice("Encrypt or decrypt?", MODE_CHOICES);

        if (oMode.isEmpty())
            return CommandResult.error("No input");

        Optional<String> oEntropy = in.string("Base64 entropy (original for encrypt, encrypted for decrypt)?");

        if (oEntropy.isEmpty())
            return CommandResult.error("No input");

        Optional<Path> oKeyPath = in.existingFilePath("Path to the RSA key?");

        if (oKeyPath.isEmpty())
            return CommandResult.error("No input");

        byte[] key;
        try {
            key = Files.readAllBytes(oKeyPath.get());
        } catch (Exception e) {
            return CommandResult.error("Failed to read key from file!");
        }

        try {
            return run(oMode.get(), key, Base64.getDecoder().decode(oEntropy.get().trim()));
        } catch (IllegalArgumentException e) {
            return CommandResult.error("Invalid base64 entropy: " + e.getMessage());
        }
    }

    public CommandResult run(boolean mode, byte[] bytesKey, byte[] entropy) {
        return run(mode ? "encrypt" : "decrypt", bytesKey, entropy);
    }

    public CommandResult run(String mode, byte[] bytesKey, byte[] entropy) {
        boolean encrypt = mode.equals("encrypt");
        Key key;

        try {
            key = encrypt ? SimpleRSA.getPublicKey(bytesKey) : SimpleRSA.getPrivateKey(bytesKey);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            return CommandResult.error("Key not valid");
        }

        byte[] result;

        try {
            result = switch (mode) {
                case "encrypt" -> SimpleRSA.encrypt((PublicKey) key, entropy);
                case "decrypt" -> SimpleRSA.decrypt((PrivateKey) key, entropy);
                case "decrypt_old" -> SimpleRSA.decryptLegacy((PrivateKey) key, entropy);
                default -> throw new IllegalArgumentException("Unknown mode: " + mode);
            };
        } catch (GeneralSecurityException e) {
            e.printStackTrace();

            return CommandResult.error("Failed");
        }

        if (!encrypt)
            return this.handler.getCommand("seed", SeedGeneratorCommand.class).orElseThrow().run(result);

        return CommandResult.of("Successful! Result:\n" + Base64.getEncoder().encodeToString(result));
    }

    @Override
    public String description() {
        return "Encrypt or decrypt seed entropy using an RSA key pair";
    }

    @Override
    public String args() {
        return "<encrypt/decrypt/decrypt_old> <base64 original/encrypted RSA entropy> <public/private RSA key path>";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
