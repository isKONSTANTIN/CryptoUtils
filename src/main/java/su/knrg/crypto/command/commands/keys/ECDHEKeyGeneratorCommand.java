package su.knrg.crypto.command.commands.keys;

import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.utils.SimpleECDHE;
import su.knrg.crypto.utils.SimpleRSA;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

public class ECDHEKeyGeneratorCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        return run();
    }

    public CommandResult run() {
        StringBuilder builder = new StringBuilder();

        try {
            KeyPair keyPair = SimpleECDHE.generateECKeys();

            builder.append("Public key (base64): ").append(SimpleECDHE.keyToBase64(keyPair.getPublic())).append("\n\n");
            builder.append("Private key (base64): ").append(SimpleECDHE.keyToBase64(keyPair.getPrivate())).append("\n");
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }

        return CommandResult.of(builder.toString());
    }

    @Override
    public String description() {
        return "Generate key pair for ECDH + AES-GCM";
    }

    @Override
    public String args() {
        return null;
    }
}
