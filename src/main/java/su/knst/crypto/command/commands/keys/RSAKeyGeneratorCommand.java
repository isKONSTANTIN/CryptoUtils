package su.knst.crypto.command.commands.keys;

import su.knst.crypto.Main;
import su.knst.crypto.TerminalWorker;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.FileUtils;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.SimpleRSA;

import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.Optional;

public class RSAKeyGeneratorCommand extends Command {
    private static final int DEFAULT_SIZE = 2048;

    private static final List<Prompts.Choice> SIZE_CHOICES = List.of(
            new Prompts.Choice("1024", "1024 bits", "Fast, not recommended for new data"),
            new Prompts.Choice("2048", "2048 bits", "Recommended default"),
            new Prompts.Choice("3072", "3072 bits", "Stronger, slower key generation"),
            new Prompts.Choice("4096", "4096 bits", "Strongest, noticeably slower")
    );

    @Override
    public CommandResult run(ParamsContainer args) {
        if (args.size() == 0)
            return runInteractive();

        Optional<Path> publicPath = args.stringV(0).map((p) -> Main.getCurrentPath().resolve(p));
        Optional<Path> privatePath = args.stringV(1).map((p) -> Main.getCurrentPath().resolve(p));
        Optional<Integer> size = args.intV(2);

        if (publicPath.isEmpty() || privatePath.isEmpty())
            return CommandResult.error("Some argument not set");

        return run(publicPath.get(), privatePath.get(), size.orElse(DEFAULT_SIZE));
    }

    private CommandResult runInteractive() {
        TerminalWorker tw = Main.getTerminalWorker();

        Optional<Path> oPublicPath = Prompts.askNewFilePath(tw, "Public key output path?");

        if (oPublicPath.isEmpty())
            return CommandResult.error("No input");

        Optional<Path> oPrivatePath = Prompts.askNewFilePath(tw, "Private key output path?");

        if (oPrivatePath.isEmpty())
            return CommandResult.error("No input");

        Optional<String> oSize = Prompts.askChoice(tw, "Key size?", SIZE_CHOICES);

        if (oSize.isEmpty())
            return CommandResult.error("No input");

        return run(oPublicPath.get(), oPrivatePath.get(), Integer.parseInt(oSize.get()));
    }

    public CommandResult run(Path publicPath, Path privatePath, int size) {
        try {
            KeyPair keyPair = SimpleRSA.generateKeyPair(size);

            FileUtils.writeOwnerOnly(publicPath, SimpleRSA.keyToBytes(keyPair.getPublic()));
            FileUtils.writeOwnerOnly(privatePath, SimpleRSA.keyToBytes(keyPair.getPrivate()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return CommandResult.of("Done!");
    }

    @Override
    public String description() {
        return "Generate an RSA key pair for encrypting/decrypting seed entropy";
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
