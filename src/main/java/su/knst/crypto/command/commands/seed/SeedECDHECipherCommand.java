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
import su.knst.crypto.utils.SimpleECDHE;

import javax.crypto.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class SeedECDHECipherCommand extends Command {
    private static final List<Prompts.Choice> MODE_CHOICES = List.of(
            new Prompts.Choice("encrypt", "Encrypt", "Encrypt entropy with a shared ECDHE secret"),
            new Prompts.Choice("decrypt", "Decrypt", "Decrypt entropy with a shared ECDHE secret")
    );

    @Override
    public CommandResult run(ParamsContainer args) {
        ArgSource in = args.size() == 0
                ? new InteractiveArgSource(Main.getTerminalWorker())
                : new ScriptedArgSource(args);

        return resolve(in);
    }

    private CommandResult resolve(ArgSource in) {
        Optional<String> oMode = in.choice("Encrypt or decrypt?", MODE_CHOICES);

        if (oMode.isEmpty())
            return CommandResult.error("No input");

        Optional<Path> oPublicKeyPath = in.existingFilePath("Path to the ECDHE public key?");

        if (oPublicKeyPath.isEmpty())
            return CommandResult.error("No input");

        Optional<Path> oPrivateKeyPath = in.existingFilePath("Path to the ECDHE private key?");

        if (oPrivateKeyPath.isEmpty())
            return CommandResult.error("No input");

        byte[] pubKey;
        try {
            pubKey = Files.readAllBytes(oPublicKeyPath.get());
        } catch (Exception e) {
            return CommandResult.error("Failed to read public key from file!");
        }

        byte[] secKey;
        try {
            secKey = Files.readAllBytes(oPrivateKeyPath.get());
        } catch (Exception e) {
            return CommandResult.error("Failed to read private key from file!");
        }

        Optional<String> oEntropy = in.string("Base64 entropy (original for encrypt, encrypted for decrypt)?");

        if (oEntropy.isEmpty())
            return CommandResult.error("No input");

        try {
            return run(oMode.get().equals("encrypt"), pubKey, secKey, Base64.getDecoder().decode(oEntropy.get().trim()));
        } catch (IllegalArgumentException e) {
            return CommandResult.error("Invalid base64 entropy: " + e.getMessage());
        }
    }

    public CommandResult run(boolean mode, byte[] publicKeyBytes, byte[] privateKeyBytes, byte[] entropy) {
        PublicKey publicKey;
        PrivateKey privateKey;
        SecretKey secretKey;

        try {
            publicKey = SimpleECDHE.getPublicKey(publicKeyBytes);
        } catch (NoSuchProviderException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();

            return CommandResult.error("Public key not valid");
        }

        try {
            privateKey = SimpleECDHE.getPrivateKey(privateKeyBytes);
        } catch (NoSuchProviderException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();

            return CommandResult.error("Private key not valid");
        }

        try {
            secretKey = SimpleECDHE.generateSharedSecret(privateKey, publicKey);
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException e) {
            e.printStackTrace();

            return CommandResult.error("Secret key failed");
        }
        byte[] result;

        try {
            result = mode ? SimpleECDHE.encrypt(secretKey, entropy) : SimpleECDHE.decrypt(secretKey, entropy);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();

            return CommandResult.error("Failed");
        }

        if (!mode)
            return this.handler.getCommand("seed", SeedGeneratorCommand.class).orElseThrow().run(result);

        return CommandResult.of("Successful! Result:\n" + Base64.getEncoder().encodeToString(result));
    }

    @Override
    public String description() {
        return "Encrypt or decrypt seed entropy using an ECDHE shared secret derived from two key pairs";
    }

    @Override
    public String args() {
        return "<encrypt/decrypt> <public ECDHE key path> <private ECDHE key path> <base64 original/encrypted ECDHE entropy>";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
