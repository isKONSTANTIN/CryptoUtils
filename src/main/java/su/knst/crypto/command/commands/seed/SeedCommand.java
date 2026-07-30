package su.knst.crypto.command.commands.seed;

import su.knst.crypto.Main;
import su.knst.crypto.cli.format.SeedFormat;
import su.knst.crypto.command.ArgSource;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.CommandResultBuilder;
import su.knst.crypto.command.InteractiveArgSource;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.ScriptedArgSource;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.core.seed.SeedException;
import su.knst.crypto.core.seed.SeedService;
import su.knst.crypto.core.seed.SeedView;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.Prompts;
import su.knst.crypto.utils.worldlists.WordLists;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Everything BIP-39, behind one leading mode question: generating a phrase, converting between a
 * phrase and its entropy in either direction, extending a short phrase, and choosing the wordlist
 * those conversions run against.
 */
public class SeedCommand extends Command {
    private static final List<Prompts.Choice> MODE_CHOICES = List.of(
            new Prompts.Choice("generate", "Generate", "Fresh random 12/24-word phrase"),
            new Prompts.Choice("from_base", "Base64 -> seed", "Rebuild a phrase from base64 entropy"),
            new Prompts.Choice("from_hex", "Hex -> seed", "Rebuild a phrase from hex entropy"),
            new Prompts.Choice("to_base", "Seed -> base64", "Show a phrase's entropy as base64"),
            new Prompts.Choice("to_hex", "Seed -> hex", "Show a phrase's entropy as hex"),
            new Prompts.Choice("extend", "Extend", "Grow a 12-word phrase into a 24-word one"),
            new Prompts.Choice("wordlist", "Wordlist", "Show or change the active BIP-39 wordlist")
    );

    private static final List<Prompts.Choice> WORDLIST_MODE_CHOICES = List.of(
            new Prompts.Choice("list", "List", "Show available wordlists"),
            new Prompts.Choice("set", "Set", "Change the active wordlist")
    );

    @Override
    public CommandResult run(ParamsContainer args) {
        ArgSource in = args.size() == 0
                ? new InteractiveArgSource(Main.getTerminalWorker())
                : new ScriptedArgSource(args);

        Optional<String> oMode = in.choice("What do you want to do with a seed phrase?", MODE_CHOICES);

        if (oMode.isEmpty())
            return CommandResult.error("No input");

        try {
            return switch (oMode.get()) {
                case "generate" -> describe(SeedService.randomEntropy(SeedService.FULL_PHRASE_ENTROPY));
                case "from_base" -> fromBase64(in);
                case "from_hex" -> fromHex(in);
                case "to_base" -> toEncoding(in, true);
                case "to_hex" -> toEncoding(in, false);
                case "extend" -> extend(in);
                case "wordlist" -> wordlist(in);
                default -> CommandResult.error("Unknown mode");
            };
        } catch (SeedException e) {
            return CommandResult.error(e.getMessage());
        }
    }

    private CommandResult fromBase64(ArgSource in) throws SeedException {
        Optional<String> oBase64 = in.string("Base64 encoded entropy?");

        if (oBase64.isEmpty())
            return CommandResult.error("No input");

        byte[] entropy;

        try {
            entropy = Base64.getDecoder().decode(oBase64.get().trim());
        } catch (IllegalArgumentException e) {
            return CommandResult.error("Not a valid base64 string: " + e.getMessage());
        }

        return describe(entropy);
    }

    private CommandResult fromHex(ArgSource in) throws SeedException {
        Optional<String> oHex = in.string("Hex encoded entropy?");

        if (oHex.isEmpty())
            return CommandResult.error("No input");

        String hex = oHex.get().trim();

        if (!HexUtils.isValidHex(hex))
            return CommandResult.error("Not a valid hex string");

        return describe(HexUtils.hexStringToByteArray(hex));
    }

    private CommandResult toEncoding(ArgSource in, boolean base64) throws SeedException {
        Optional<String[]> oWords = in.words("Enter seed words separated by spaces:");

        if (oWords.isEmpty())
            return CommandResult.error("No input");

        byte[] entropy = SeedService.toEntropy(oWords.get());

        return CommandResultBuilder.builder()
                .line("Source entropy:")
                .line(SeedFormat.formatBits(entropy, 4))
                .line()
                .line(base64
                        ? "Base64 encoded: " + Base64.getEncoder().encodeToString(entropy)
                        : "Hex encoded: " + HexUtils.bytesToHex(entropy))
                .build();
    }

    private CommandResult extend(ArgSource in) throws SeedException {
        Optional<String[]> oWords = in.words("Enter 12 seed words separated by spaces:");

        if (oWords.isEmpty())
            return CommandResult.error("No input");

        if (oWords.get().length != 12)
            return CommandResult.error("Wrong mnemonic size");

        byte[] entropy = SeedService.toEntropy(oWords.get());
        byte[] extended = SeedService.extend(entropy);

        CommandResultBuilder builder = CommandResultBuilder.builder()
                .line("Source entropy:")
                .line(SeedFormat.formatBits(entropy, 4))
                .line()
                .line("Extended entropy:")
                .line(SeedFormat.formatBits(extended, 4))
                .line();

        return builder.merge(describe(extended)).build();
    }

    private CommandResult wordlist(ArgSource in) {
        Optional<String> oMode = in.choice("List available wordlists, or set the active one?", WORDLIST_MODE_CHOICES);

        if (oMode.isEmpty())
            return CommandResult.error("No input");

        if (oMode.get().equals("list"))
            return CommandResult.of(String.join("\n", WordLists.getLists()) + "\n");

        List<Prompts.Choice> nameChoices = WordLists.getLists().stream()
                .map(name -> new Prompts.Choice(name, name))
                .toList();

        Optional<String> oName = in.choice("Which wordlist?", nameChoices);

        if (oName.isEmpty())
            return CommandResult.error("No input");

        WordLists.WordList newWordList = WordLists.setActiveList(oName.get());

        if (newWordList == null)
            return CommandResult.error("List not found");

        return CommandResult.of("Now wordlist is '" + newWordList.name() + "'");
    }

    private static CommandResult describe(byte[] entropy) throws SeedException {
        SeedView view = SeedService.describe(entropy);

        return CommandResult.panels(SeedFormat.panels(view));
    }

    @Override
    public String description() {
        return "Generate, convert, extend and inspect BIP-39 seed phrases, and choose the active wordlist";
    }

    @Override
    public String args() {
        return "generate | from_base | from_hex | to_base | to_hex | extend | wordlist";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOCURRENCIES;
    }
}
