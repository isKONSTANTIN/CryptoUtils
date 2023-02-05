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
            return CommandResult.of("Some argument not set", true);

        if (!(oMode.get().equals("encrypt") || oMode.get().equals("decrypt")))
            return CommandResult.of("Mode must be 'encrypt' or 'decrypt'", true);

        byte[] key;
        try {
            key = Files.readAllBytes(oKey.get());
        } catch (Exception e) {
            return CommandResult.of("Failed to read key from file!", true);
        }

        return run(oMode.get().equals("encrypt"), key, Base64.getDecoder().decode(oEntropy.get()));
    }

    public CommandResult run(boolean mode, byte[] bytesKey, byte[] entropy) {
        Key key;

        try {
            key = mode ? SimpleRSA.getPublicKey(bytesKey) : SimpleRSA.getPrivateKey(bytesKey);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            return CommandResult.of("Key not valid", true);
        }

        byte[] result;

        try {
            result = mode ? SimpleRSA.encrypt((PublicKey) key, entropy) : SimpleRSA.decrypt((PrivateKey) key, entropy);
        } catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException e) {
            System.err.println("Error:");
            throw new RuntimeException(e);
        }

        if (!mode)
            return this.handler.getCommand("seed", SeedGeneratorCommand.class).run(result);

        return CommandResult.of("Successful! Result:\n" + Base64.getEncoder().encodeToString(result));
    }

    @Override
    public String description() {
        return "Encrypt/decrypt entropy by RSA";
    }

    @Override
    public String args() {
        return "<encrypt/decrypt> <base64 original/encrypted RSA entropy> <public/private RSA key path>";
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

                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
