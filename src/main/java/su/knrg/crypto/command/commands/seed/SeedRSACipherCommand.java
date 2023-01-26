package su.knrg.crypto.command.commands.seed;

import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.command.commands.CommandTag;
import su.knrg.crypto.utils.SimpleFileWorker;
import su.knrg.crypto.utils.SimpleRSA;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Optional;

public class SeedRSACipherCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> oMode = args.stringV(0);
        Optional<String> oEntropy = args.stringV(1);
        Optional<String> oKey = args.stringV(2);

        if (oMode.isEmpty() || oKey.isEmpty() || oEntropy.isEmpty())
            return CommandResult.of("Some argument not set", true);

        if (!(oMode.get().equals("encrypt") || oMode.get().equals("decrypt")))
            return CommandResult.of("Mode must be 'encrypt' or 'decrypt'", true);

        byte[] key;
        try {
            key = SimpleFileWorker.of(oKey.get()).readBytesFromFile();
        } catch (Exception e) {
            return CommandResult.of("Failed to read key from file!", true);
        }

        return run(oMode.get().equals("encrypt"), key, Base64.getDecoder().decode(oEntropy.get()));
    }

    public CommandResult run(boolean mode, byte[] bytesKey, byte[] entropy) {
        Key key = null;

        try {
            key = mode ? SimpleRSA.getPublicKey(bytesKey) : SimpleRSA.getPrivateKey(bytesKey);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            return CommandResult.of("Key not valid", true);
        }

        byte[] result = null;

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
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
