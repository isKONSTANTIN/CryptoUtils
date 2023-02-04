package su.knrg.crypto.command.commands.keys;

import org.jline.builtins.Completers;
import su.knrg.crypto.Main;
import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.command.commands.CommandTag;
import su.knrg.crypto.utils.SimpleECDHE;
import su.knrg.crypto.utils.args.ArgsTreeBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Optional;

public class ECDHEKeyGeneratorCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<Path> publicPath = args.stringV(0).map((p) -> Main.getCurrentPath().resolve(p));
        Optional<Path> privatePath = args.stringV(1).map((p) -> Main.getCurrentPath().resolve(p));

        if (publicPath.isEmpty() || privatePath.isEmpty())
            return CommandResult.of("Some argument not set", true);

        return run(publicPath.get(), privatePath.get());
    }

    public CommandResult run(Path publicPath, Path privatePath) {
        try {
            KeyPair keyPair = SimpleECDHE.generateECKeys();

            Files.write(publicPath, SimpleECDHE.keyToBytes(keyPair.getPublic()));
            Files.write(privatePath, SimpleECDHE.keyToBytes(keyPair.getPrivate()));
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

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .recursiveSubTree()
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .parent()
                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOGRAPHY;
    }
}
