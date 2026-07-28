package su.knst.crypto.command.commands.seed;

import su.knst.crypto.Main;
import su.knst.crypto.TerminalWorker;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.SimpleECDHE;
import su.knst.crypto.utils.TerminalQuestion;

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
        if (args.size() == 0)
            return runInteractive();

        Optional<String> oMode = args.stringV(0);
        Optional<Path> oPublicKey = args.stringV(1).map((p) -> Main.getCurrentPath().resolve(p));
        Optional<Path> oPrivateKey = args.stringV(2).map((p) -> Main.getCurrentPath().resolve(p));
        Optional<String> oEntropy = args.stringV(3);

        if (oMode.isEmpty() || oPublicKey.isEmpty() || oPrivateKey.isEmpty() || oEntropy.isEmpty())
            return CommandResult.error("Some argument not set");

        if (!(oMode.get().equals("encrypt") || oMode.get().equals("decrypt")))
            return CommandResult.error("Mode must be 'encrypt' or 'decrypt'");

        byte[] pubKey;
        try {
            pubKey = Files.readAllBytes(oPublicKey.get());
        } catch (Exception e) {
            return CommandResult.error("Failed to read public key from file!");
        }

        byte[] secKey;
        try {
            secKey = Files.readAllBytes(oPrivateKey.get());
        } catch (Exception e) {
            return CommandResult.error("Failed to read private key from file!");
        }

        try {
            return run(oMode.get().equals("encrypt"), pubKey, secKey, Base64.getDecoder().decode(oEntropy.get()));
        } catch (IllegalArgumentException e) {
            return CommandResult.error("Invalid base64 entropy: " + e.getMessage());
        }
    }

    private CommandResult runInteractive() {
        TerminalWorker tw = Main.getTerminalWorker();

        Optional<String> oMode = Prompts.askChoice(tw, "Encrypt or decrypt?", MODE_CHOICES);

        if (oMode.isEmpty())
            return CommandResult.error("No input");

        Optional<Path> oPublicKeyPath = Prompts.askExistingFilePath(tw, "Path to the ECDHE public key?");

        if (oPublicKeyPath.isEmpty())
            return CommandResult.error("No input");

        Optional<Path> oPrivateKeyPath = Prompts.askExistingFilePath(tw, "Path to the ECDHE private key?");

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

        Optional<String> oEntropy = tw.ask(new TerminalQuestion("Base64 entropy (original for encrypt, encrypted for decrypt)?", null));

        if (oEntropy.isEmpty() || oEntropy.get().isBlank())
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
