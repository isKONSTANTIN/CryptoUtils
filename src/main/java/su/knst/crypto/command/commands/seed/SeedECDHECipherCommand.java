package su.knst.crypto.command.commands.seed;

import org.jline.builtins.Completers;
import su.knst.crypto.Main;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.SimpleECDHE;
import su.knst.crypto.utils.args.ArgsTreeBuilder;

import javax.crypto.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Optional;

public class SeedECDHECipherCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
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

        return run(oMode.get().equals("encrypt"), pubKey, secKey, Base64.getDecoder().decode(oEntropy.get()));
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
        return "Encrypt/decrypt entropy by ECDHE";
    }

    @Override
    public String args() {
        return "<encrypt/decrypt> <public ECDHE key path> <private ECDHE key path> <base64 original/encrypted ECDHE entropy>";
    }

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .subTree().addPossibleArg("encrypt")

                .recursiveSubTree()
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .addTip("<base64 original entropy>", "Seed entropy")
                .parent()

                .parent()

                .subTree().addPossibleArg("decrypt")

                .recursiveSubTree()
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .addTip("<base64 encrypted ECDHE entropy>", "Encrypted ECDHE seed entropy")
                .parent()

                .parent()

                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
