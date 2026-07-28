package su.knst.crypto.command.commands.shamir;

import com.codahale.shamir.Scheme;
import su.knst.crypto.Main;
import su.knst.crypto.command.ArgSource;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.InteractiveArgSource;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.ScriptedArgSource;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.FileUtils;
import su.knst.crypto.utils.Prompts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ShamirCommand extends Command {
    private static final List<Prompts.Choice> MODE_CHOICES = List.of(
            new Prompts.Choice("split", "Split", "Split a file into Shamir secret-sharing parts"),
            new Prompts.Choice("join", "Join", "Reconstruct a file from Shamir secret-sharing parts")
    );

    @Override
    public CommandResult run(ParamsContainer args) {
        ArgSource in = args.size() == 0
                ? new InteractiveArgSource(Main.getTerminalWorker())
                : new ScriptedArgSource(args);

        return resolve(in);
    }

    private CommandResult resolve(ArgSource in) {
        Optional<String> oMode = in.choice("Split a file into parts, or join parts back together?", MODE_CHOICES);

        if (oMode.isEmpty())
            return CommandResult.error("No input");

        if (oMode.get().equals("split")) {
            Optional<Integer> oAllParts = in.integer("Number of all parts (N)?");

            if (oAllParts.isEmpty())
                return CommandResult.error("No input");

            Optional<Integer> oForRecover = in.integer("Number of parts required to recover (K)?");

            if (oForRecover.isEmpty())
                return CommandResult.error("No input");

            Optional<Path> oPath = in.existingFilePath("Path to file to split?");

            if (oPath.isEmpty())
                return CommandResult.error("No input");

            return split(oAllParts.get(), oForRecover.get(), oPath.get());
        }

        Optional<Path> oResultPath = in.newFilePath("Result path for the reconstructed file?");

        if (oResultPath.isEmpty())
            return CommandResult.error("No input");

        Map<Integer, byte[]> parts = new HashMap<>();

        try {
            // Scripted mode has no separate "total" argument - the part list is however many
            // positional tokens are left. Interactive mode has to ask for a count up front
            // instead, since there's no natural end-of-input signal on a single prompt loop.
            if (in.interactive()) {
                Optional<Integer> oTotal = in.integer("How many parts were there in total?");

                if (oTotal.isEmpty())
                    return CommandResult.error("No input");

                for (int i = 1; i <= oTotal.get(); i++) {
                    Optional<Path> oPart = in.existingFilePath("Part #" + i + " file path (empty to skip):");

                    if (oPart.isEmpty())
                        continue;

                    parts.put(i, Files.readAllBytes(oPart.get()));
                }
            } else {
                int index = 0;
                Optional<String> oToken;

                while ((oToken = in.string(null)).isPresent()) {
                    index++;
                    String token = oToken.get();

                    if (token.equals("null"))
                        continue;

                    parts.put(index, Files.readAllBytes(Main.getCurrentPath().resolve(token)));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();

            return CommandResult.error("Failed to read files");
        }

        return join(oResultPath.get(), parts);
    }

    private CommandResult split(int allParts, int forRecover, Path path) {
        byte[] data;

        try {
            data = Files.readAllBytes(path);
        } catch (IOException e) {
            e.printStackTrace();

            return CommandResult.error("Failed to read file");
        }

        String filename = path.toFile().getName()
                .replaceFirst("[.][^.]+$", ""); // remove extension

        Scheme scheme = new Scheme(new SecureRandom(), allParts, forRecover);
        Map<Integer, byte[]> parts = scheme.split(data);

        try {
            for (Map.Entry<Integer, byte[]> entry : parts.entrySet())
                FileUtils.writeOwnerOnly(Path.of(filename + ".shp-" + entry.getKey()), entry.getValue());
        } catch (Exception e) {
            e.printStackTrace();

            return CommandResult.error("Failed to write files");
        }

        return CommandResult.of("Done!");
    }

    private CommandResult join(Path resultPath, Map<Integer, byte[]> parts) {
        Scheme scheme = new Scheme(new SecureRandom(), 5, 4);

        try {
            FileUtils.writeOwnerOnly(resultPath, scheme.join(parts));
        } catch (IOException e) {
            e.printStackTrace();

            return CommandResult.error("Failed to write result");
        }

        return CommandResult.of("Done!");
    }

    @Override
    public String description() {
        return "Shamir's secret sharing algorithm: split a file into N parts so that any K of them can reconstruct it, or join parts back into the original file";
    }

    @Override
    public String args() {
        return "split <all parts> <parts for recover> <path> | join <result path> <part 1 | null> <part 2 | null> <part 3 | null> ...";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOGRAPHY;
    }
}
