package su.knrg.crypto.command.commands.keys;

import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.utils.SimpleRSA;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

public class RSAKeyGeneratorCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        return run();
    }

    public CommandResult run() {
        StringBuilder builder = new StringBuilder();

        try {
            KeyPair keyPair = SimpleRSA.generateKeyPair(2048);

            builder.append("Public key (base64): ").append(SimpleRSA.keyToBase64(keyPair.getPublic())).append("\n\n");
            builder.append("Private key (base64): ").append(SimpleRSA.keyToBase64(keyPair.getPrivate())).append("\n");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        return CommandResult.of(builder.toString());
    }

    @Override
    public String description() {
        return "Generate key pair for RSA";
    }

    @Override
    public String args() {
        return null;
    }
}
