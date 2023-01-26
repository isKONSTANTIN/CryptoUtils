package su.knrg.crypto.command.commands.keys;

import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.command.commands.CommandTag;
import su.knrg.crypto.utils.SimpleFileWorker;
import su.knrg.crypto.utils.SimpleRSA;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

public class RSAKeyGeneratorCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> publicPath = args.stringV(0);
        Optional<String> privatePath = args.stringV(1);
        Optional<Integer> size = args.intV(2);

        if (publicPath.isEmpty() || privatePath.isEmpty())
            return CommandResult.of("Some argument not set", true);

        return run(publicPath.get(), privatePath.get(), size.orElse(2048));
    }

    public CommandResult run(String publicPath, String privatePath, int size) {
        try {
            KeyPair keyPair = SimpleRSA.generateKeyPair(size);

            SimpleFileWorker.of(publicPath).writeToFile(SimpleRSA.keyToBytes(keyPair.getPublic()));
            SimpleFileWorker.of(privatePath).writeToFile(SimpleRSA.keyToBytes(keyPair.getPrivate()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return CommandResult.of("Done!");
    }

    @Override
    public String description() {
        return "Generate key pair for RSA";
    }

    @Override
    public String args() {
        return "<public RSA key file path> <private RSA key file path> [keys size]";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOGRAPHY;
    }
}
