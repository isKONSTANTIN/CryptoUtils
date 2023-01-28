package su.knrg.crypto.command.commands.seed;

import org.jline.builtins.Completers;
import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.command.commands.CommandTag;
import su.knrg.crypto.utils.SimpleECDHE;
import su.knrg.crypto.utils.args.ArgsTreeBuilder;

import javax.crypto.*;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Optional;

public class SeedECDHECipherCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> oMode = args.stringV(0);
        Optional<String> oPublicKey = args.stringV(1);
        Optional<String> oPrivateKey = args.stringV(2);
        Optional<String> oEntropy = args.stringV(3);

        if (oMode.isEmpty() || oPublicKey.isEmpty() || oPrivateKey.isEmpty() || oEntropy.isEmpty())
            return CommandResult.of("Some argument not set", true);

        if (!(oMode.get().equals("encrypt") || oMode.get().equals("decrypt")))
            return CommandResult.of("Mode must be 'encrypt' or 'decrypt'", true);

        return run(oMode.get().equals("encrypt"), oPublicKey.get(), oPrivateKey.get(), Base64.getDecoder().decode(oEntropy.get()));
    }

    public CommandResult run(boolean mode, String base64PublicKey, String base64PrivateKey, byte[] entropy) {
        PublicKey publicKey = null;
        PrivateKey privateKey = null;
        SecretKey secretKey = null;

        try {
            publicKey = SimpleECDHE.base64ToPublicKey(base64PublicKey);
        } catch (NoSuchProviderException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            return CommandResult.of("Public key not valid", true);
        }

        try {
            privateKey = SimpleECDHE.base64ToPrivateKey(base64PrivateKey);
        } catch (NoSuchProviderException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            return CommandResult.of("Private key not valid", true);
        }

        try {
            secretKey = SimpleECDHE.generateSharedSecret(privateKey, publicKey);
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException e) {
            e.printStackTrace();
            return CommandResult.of("Secret key failed", true);
        }
        byte[] result;

        try {
            result = mode ? SimpleECDHE.encrypt(secretKey, entropy) : SimpleECDHE.decrypt(secretKey, entropy);
        } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | ShortBufferException |
                 IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException | NoSuchProviderException |
                 InvalidKeyException e) {
            System.err.println("Error:");
            e.printStackTrace();
            return CommandResult.of("Failed", true);
        }

        if (!mode)
            return this.handler.getCommand("seed", SeedGeneratorCommand.class).run(result);

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
                .addCompleter(new Completers.FilesCompleter(Path.of("./")))
                .addCompleter(new Completers.FilesCompleter(Path.of("./")))
                .addTip("<base64 original entropy>", "Seed entropy")
                .parent()

                .parent()

                .subTree().addPossibleArg("decrypt")

                .recursiveSubTree()
                .addCompleter(new Completers.FilesCompleter(Path.of("./")))
                .addCompleter(new Completers.FilesCompleter(Path.of("./")))
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
