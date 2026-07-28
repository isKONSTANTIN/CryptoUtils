package su.knst.crypto.command.commands.keys;

import su.knst.crypto.Main;
import su.knst.crypto.TerminalWorker;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.FileUtils;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.SimpleECDHE;

import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Optional;

public class ECDHEKeyGeneratorCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        if (args.size() == 0)
            return runInteractive();

        Optional<Path> publicPath = args.stringV(0).map((p) -> Main.getCurrentPath().resolve(p));
        Optional<Path> privatePath = args.stringV(1).map((p) -> Main.getCurrentPath().resolve(p));

        if (publicPath.isEmpty() || privatePath.isEmpty())
            return CommandResult.error("Some argument not set");

        return run(publicPath.get(), privatePath.get());
    }

    private CommandResult runInteractive() {
        TerminalWorker tw = Main.getTerminalWorker();

        Optional<Path> oPublicPath = Prompts.askNewFilePath(tw, "Public key output path?");

        if (oPublicPath.isEmpty())
            return CommandResult.error("No input");

        Optional<Path> oPrivatePath = Prompts.askNewFilePath(tw, "Private key output path?");

        if (oPrivatePath.isEmpty())
            return CommandResult.error("No input");

        return run(oPublicPath.get(), oPrivatePath.get());
    }

    public CommandResult run(Path publicPath, Path privatePath) {
        try {
            KeyPair keyPair = SimpleECDHE.generateECKeys();

            FileUtils.writeOwnerOnly(publicPath, SimpleECDHE.keyToBytes(keyPair.getPublic()));
            FileUtils.writeOwnerOnly(privatePath, SimpleECDHE.keyToBytes(keyPair.getPrivate()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return CommandResult.of("Done!");
    }

    @Override
    public String description() {
        return "Generate an ECDHE key pair for deriving a shared AES-GCM secret with another party";
    }

    @Override
    public String args() {
        return "<public ECDHE key file path> <private ECDHE key file path>";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOGRAPHY;
    }
}
