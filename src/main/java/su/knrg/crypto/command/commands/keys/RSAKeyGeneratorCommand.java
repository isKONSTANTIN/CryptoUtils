package su.knrg.crypto.command.commands.keys;

import org.jline.builtins.Completers;
import su.knrg.crypto.Main;
import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.command.commands.CommandTag;
import su.knrg.crypto.utils.SimpleRSA;
import su.knrg.crypto.utils.args.ArgsTreeBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Optional;

public class RSAKeyGeneratorCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<Path> publicPath = args.stringV(0).map((p) -> Main.getCurrentPath().resolve(p));
        Optional<Path> privatePath = args.stringV(1).map((p) -> Main.getCurrentPath().resolve(p));
        Optional<Integer> size = args.intV(2);

        if (publicPath.isEmpty() || privatePath.isEmpty())
            return CommandResult.of("Some argument not set", true);

        return run(publicPath.get(), privatePath.get(), size.orElse(2048));
    }

    public CommandResult run(Path publicPath, Path privatePath, int size) {
        try {
            KeyPair keyPair = SimpleRSA.generateKeyPair(size);

            Files.write(publicPath, SimpleRSA.keyToBytes(keyPair.getPublic()));
            Files.write(privatePath, SimpleRSA.keyToBytes(keyPair.getPrivate()));
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
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .recursiveSubTree()
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .addTip("[keys size]", "Key size in bits")
                .parent()
                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOGRAPHY;
    }
}
