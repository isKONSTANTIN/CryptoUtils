package su.knst.crypto.command.commands.seed;

import org.jline.builtins.Completers;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.args.ArgsTreeBuilder;

import java.util.Optional;

public class HexToSeedCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> oHex = args.stringV(0);

        if (oHex.isEmpty())
            return CommandResult.error("Hex entropy string required");

        String hex = oHex.get();

        if (!HexUtils.isValidHex(hex))
            return CommandResult.error("Failed to run: invalid hex string");

        byte[] entropy = HexUtils.hexStringToByteArray(hex);

        return new SeedGeneratorCommand().run(entropy);
    }

    @Override
    public String description() {
        return "Restore 12 and 24 word seed phrase from hex entropy";
    }

    @Override
    public String args() {
        return "<hex string>";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .addTip("<hex string>", "Seed bytes in hex")
                .build();
    }
}
