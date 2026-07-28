package su.knst.crypto.command.commands.shamir;

import com.codahale.shamir.Scheme;
import su.knst.crypto.Main;
import su.knst.crypto.TerminalWorker;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
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
        if (args.size() == 0)
            return runInteractive();

        return runScripted(args);
    }

    private CommandResult runScripted(ParamsContainer args) {
        Optional<String> oMode = args.stringV(0);

        if (oMode.isEmpty())
            return CommandResult.error("Mode not set");

        if (!(oMode.get().equals("split") || oMode.get().equals("join")))
            return CommandResult.error("Mode must be 'split' or 'join'");

        boolean mode = oMode.map((s) -> s.equals("split")).get();

        if (mode) {
            Optional<Integer> oAllParts = args.intV(1);

            if (oAllParts.isEmpty())
                return CommandResult.error("All parts not set");

            Optional<Integer> oForRecover = args.intV(2);

            if (oForRecover.isEmpty())
                return CommandResult.error("Parts for recover not set");

            Optional<Path> oPath = args.stringV(3).map((p) -> Main.getCurrentPath().resolve(p));

            if (oPath.isEmpty())
                return CommandResult.error("Path not set");

            return split(oAllParts.get(), oForRecover.get(), oPath.get());
        } else {
            Optional<Path> oResultPath = args.stringV(1).map((p) -> Main.getCurrentPath().resolve(p));

            if (oResultPath.isEmpty())
                return CommandResult.error("Result path not set");

            HashMap<Integer, byte[]> parts = new HashMap<>();

            try {
                for (int i = 2; i < args.size(); i++) {
                    Optional<String> path = args.stringV(i);

                    if (path.isEmpty() || path.get().equals("null"))
                        continue;

                    parts.put(i - 1, Files.readAllBytes(path.map((p) -> Main.getCurrentPath().resolve(p)).get()));
                }
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.error("Failed to read files");
            }

            return join(oResultPath.get(), parts);
        }
    }

    private CommandResult runInteractive() {
        TerminalWorker tw = Main.getTerminalWorker();

        Optional<String> oMode = Prompts.askChoice(tw, "Split a file into parts, or join parts back together?", MODE_CHOICES);

        if (oMode.isEmpty())
            return CommandResult.error("No input");

        if (oMode.get().equals("split")) {
            Optional<Integer> oAllParts = Prompts.askInt(tw, "Number of all parts (N)?");

            if (oAllParts.isEmpty())
                return CommandResult.error("No input");

            Optional<Integer> oForRecover = Prompts.askInt(tw, "Number of parts required to recover (K)?");

            if (oForRecover.isEmpty())
                return CommandResult.error("No input");

            Optional<Path> oPath = Prompts.askExistingFilePath(tw, "Path to file to split?");

            if (oPath.isEmpty())
                return CommandResult.error("No input");

            return split(oAllParts.get(), oForRecover.get(), oPath.get());
        } else {
            Optional<Path> oResultPath = Prompts.askNewFilePath(tw, "Result path for the reconstructed file?");

            if (oResultPath.isEmpty())
                return CommandResult.error("No input");

            Optional<Integer> oTotal = Prompts.askInt(tw, "How many parts were there in total?");

            if (oTotal.isEmpty())
                return CommandResult.error("No input");

            Map<Integer, byte[]> parts = new HashMap<>();

            for (int i = 1; i <= oTotal.get(); i++) {
                Optional<Path> oPart = Prompts.askExistingFilePath(tw, "Part #" + i + " file path (empty to skip):");

                if (oPart.isEmpty())
                    continue;

                try {
                    parts.put(i, Files.readAllBytes(oPart.get()));
                } catch (IOException e) {
                    e.printStackTrace();

                    return CommandResult.error("Failed to read part #" + i);
                }
            }

            return join(oResultPath.get(), parts);
        }
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
