package su.knrg.crypto.command.commands.keys;

import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.utils.SimpleECDHE;
import su.knrg.crypto.utils.SimpleFileWorker;
import su.knrg.crypto.utils.SimpleRSA;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Optional;

public class ECDHEKeyGeneratorCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> publicPath = args.stringV(0);
        Optional<String> privatePath = args.stringV(1);

        if (publicPath.isEmpty() || privatePath.isEmpty())
            return CommandResult.of("Some argument not set", true);

        return run(publicPath.get(), privatePath.get());
    }

    public CommandResult run(String publicPath, String privatePath) {
        try {
            KeyPair keyPair = SimpleECDHE.generateECKeys();

            SimpleFileWorker.of(publicPath).writeToFile(SimpleECDHE.keyToBytes(keyPair.getPublic()));
            SimpleFileWorker.of(privatePath).writeToFile(SimpleECDHE.keyToBytes(keyPair.getPrivate()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return CommandResult.of("Done!");
    }

    @Override
    public String description() {
        return "Generate key pair for ECDH + AES-GCM";
    }

    @Override
    public String args() {
        return "<public ECDHE key file path> <private ECDHE key file path>";
    }
}
