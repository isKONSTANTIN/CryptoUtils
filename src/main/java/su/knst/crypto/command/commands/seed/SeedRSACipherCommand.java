package su.knst.crypto.command.commands.seed;

import org.jline.builtins.Completers;
import su.knst.crypto.Main;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.SimpleRSA;
import su.knst.crypto.utils.args.ArgsTreeBuilder;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Optional;

public class SeedRSACipherCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> oMode = args.stringV(0);
        Optional<String> oEntropy = args.stringV(1);
        Optional<Path> oKey = args.stringV(2).map((p) -> Main.getCurrentPath().resolve(p));

        if (oMode.isEmpty() || oKey.isEmpty() || oEntropy.isEmpty())
            return CommandResult.error("Some argument not set");

        if (!(oMode.get().equals("encrypt") || oMode.get().equals("decrypt") || oMode.get().equals("decrypt_old")))
            return CommandResult.error("Mode must be 'encrypt', 'decrypt' or 'decrypt_old'");

        byte[] key;
        try {
            key = Files.readAllBytes(oKey.get());
        } catch (Exception e) {
            return CommandResult.error("Failed to read key from file!");
        }

        return run(oMode.get(), key, Base64.getDecoder().decode(oEntropy.get()));
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
        return "Encrypt/decrypt entropy by RSA ('decrypt_old' reads backups made before the switch to OAEP padding)";
    }

    @Override
    public String args() {
        return "<encrypt/decrypt/decrypt_old> <base64 original/encrypted RSA entropy> <public/private RSA key path>";
    }

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .subTree().addPossibleArg("encrypt")

                .recursiveSubTree()
                .addTip("<base64 original entropy>", "Seed entropy")
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .parent()

                .parent()

                .subTree().addPossibleArg("decrypt")

                .recursiveSubTree()
                .addTip("<base64 encrypted RSA entropy>", "RSA seed entropy")
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .parent()

                .parent()

                .subTree().addPossibleArg("decrypt_old")

                .recursiveSubTree()
                .addTip("<base64 encrypted RSA entropy>", "RSA seed entropy (legacy PKCS#1 v1.5)")
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .parent()

                .parent()

                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
