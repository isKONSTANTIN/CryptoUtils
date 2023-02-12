package su.knst.crypto.command.commands.shamir;

import com.codahale.shamir.Scheme;
import org.jline.builtins.Completers;
import su.knst.crypto.Main;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.args.ArgsTreeBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ShamirCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
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

            byte[] data;

            try {
                data = Files.readAllBytes(oPath.get());
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.error("Failed to read file");
            }

            String filename = oPath.get().toFile().getName()
                    .replaceFirst("[.][^.]+$", ""); // remove extension

            Scheme scheme = new Scheme(new SecureRandom(), oAllParts.get(), oForRecover.get());
            Map<Integer, byte[]> parts = scheme.split(data);

            try {
                for (Map.Entry<Integer, byte[]> entry : parts.entrySet())
                    Files.write(Path.of(filename + ".shp-" + entry.getKey()), entry.getValue());
            }catch (Exception e) {
                e.printStackTrace();

                return CommandResult.error("Failed to write files");
            }
        }else {
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

            Scheme scheme = new Scheme(new SecureRandom(), 5, 4);

            try {
                Files.write(oResultPath.get(), scheme.join(parts));
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.error("Failed to write result");
            }
        }

        return CommandResult.of("Done!");
    }

    @Override
    public String description() {
        return "Shamir's secret sharing algorithm";
    }

    @Override
    public String args() {
        return "split <all parts> <parts for recover> <path> | join <result path> <part 1 | null> <part 2 | null> <part 3 | null> ...";
    }

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .subTree().addPossibleArg("split")

                .recursiveSubTree()
                .addTip("<all parts>", "Number of all parts")
                .addTip("<parts for recover>", "Number of required parts for recover")
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .parent()

                .parent()

                .subTree().addPossibleArg("join")
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .parent()

                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.CRYPTOGRAPHY;
    }
}
