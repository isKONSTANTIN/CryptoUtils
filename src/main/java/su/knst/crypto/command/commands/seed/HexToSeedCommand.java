package su.knst.crypto.command.commands.seed;

import su.knst.crypto.Main;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.TerminalQuestion;

import java.util.Optional;

public class HexToSeedCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        if (args.size() == 0)
            return runInteractive();

        Optional<String> oHex = args.stringV(0);

        return run(oHex.orElseThrow());
    }

    private CommandResult runInteractive() {
        Optional<String> oHex = Main.getTerminalWorker().ask(new TerminalQuestion("Hex entropy string?", null));

        if (oHex.isEmpty() || oHex.get().isBlank())
            return CommandResult.error("No input");

        return run(oHex.get().trim());
    }

    private CommandResult run(String hex) {
        if (!HexUtils.isValidHex(hex))
            return CommandResult.error("Failed to run: invalid hex string");

        byte[] entropy = HexUtils.hexStringToByteArray(hex);

        return new SeedGeneratorCommand().run(entropy);
    }

    @Override
    public String description() {
        return "Restore a 12/24-word seed phrase from raw hex entropy";
    }

    @Override
    public String args() {
        return "<hex string>";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
