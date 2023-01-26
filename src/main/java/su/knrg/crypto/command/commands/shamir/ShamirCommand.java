package su.knrg.crypto.command.commands.shamir;

import com.codahale.shamir.Scheme;
import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.command.commands.CommandTag;
import su.knrg.crypto.utils.SimpleECDHE;
import su.knrg.crypto.utils.SimpleFileWorker;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ShamirCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> oMode = args.stringV(0);

        if (oMode.isEmpty())
            return CommandResult.of("Mode not set", true);

        if (!(oMode.get().equals("split") || oMode.get().equals("join")))
            return CommandResult.of("Mode must be 'split' or 'join'", true);

        boolean mode = oMode.map((s) -> s.equals("split")).get();

        if (mode) {
            Optional<Integer> oAllParts = args.intV(1);

            if (oAllParts.isEmpty())
                return CommandResult.of("All parts not set", true);

            Optional<Integer> oForRecover = args.intV(2);

            if (oForRecover.isEmpty())
                return CommandResult.of("Parts for recover not set", true);

            Optional<String> oPath = args.stringV(3);

            if (oPath.isEmpty())
                return CommandResult.of("Path not set", true);

            byte[] data;

            try {
                data = SimpleFileWorker.of(oPath.get()).readBytesFromFile();
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.of("Failed to read file", true);
            }

            String filename = new File(oPath.get()).getName()
                    .replaceFirst("[.][^.]+$", ""); // remove extension

            Scheme scheme = new Scheme(new SecureRandom(), oAllParts.get(), oForRecover.get());
            Map<Integer, byte[]> parts = scheme.split(data);

            try {
                for (Map.Entry<Integer, byte[]> entry : parts.entrySet())
                    SimpleFileWorker.of(filename + ".shp-" + entry.getKey()).writeToFile(entry.getValue());
            }catch (Exception e) {
                e.printStackTrace();

                return CommandResult.of("Failed to write files", true);
            }
        }else {
            Optional<String> oResultPath = args.stringV(1);

            if (oResultPath.isEmpty())
                return CommandResult.of("Result path not set", true);

            HashMap<Integer, byte[]> parts = new HashMap<>();

            try {
                for (int i = 2; i < args.size(); i++) {
                    Optional<String> path = args.stringV(i);

                    if (path.isEmpty() || path.get().equals("null"))
                        continue;

                    parts.put(i - 1, SimpleFileWorker.of(path.get()).readBytesFromFile());
                }
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.of("Failed to read files", true);
            }

            Scheme scheme = new Scheme(new SecureRandom(), 5, 4);

            try {
                SimpleFileWorker.of(oResultPath.get()).writeToFile(scheme.join(parts));
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.of("Failed to write result", true);
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
    public CommandTag tag() {
        return CommandTag.CRYPTOGRAPHY;
    }
}
