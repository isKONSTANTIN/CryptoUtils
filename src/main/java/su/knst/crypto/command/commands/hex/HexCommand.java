package su.knst.crypto.command.commands.hex;

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
import java.util.Optional;

public class HexCommand extends Command {
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> oMode = args.stringV(0);

        if (oMode.isEmpty())
            return CommandResult.of("Mode not set", true);

        if (!(oMode.get().equals("encode") || oMode.get().equals("decode")))
            return CommandResult.of("Mode must be 'encode' or 'decode'", true);

        Optional<Path> oSource = args.stringV(1).map((p) -> Main.getCurrentPath().resolve(p));

        if (oSource.isEmpty())
            return CommandResult.of("Source path not set", true);

        Optional<Path> oResult = args.stringV(2).map((p) -> Main.getCurrentPath().resolve(p));

        if (oResult.isEmpty())
            return CommandResult.of("Result path not set", true);

        boolean mode = oMode.map((s) -> s.equals("encode")).get();

        if (mode) {
            byte[] bytes;

            try {
                bytes = Files.readAllBytes(oSource.get());
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.of("Failed to read source file", true);
            }

            try {
                Files.writeString(oResult.get(), bytesToHex(bytes));
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.of("Failed to write result file", true);
            }
        }else {
            String hex;

            try {
                hex = Files.readString(oSource.get());
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.of("Failed to read source file", true);
            }

            try {
                Files.write(oResult.get(), hexStringToByteArray(hex));
            } catch (IOException e) {
                e.printStackTrace();

                return CommandResult.of("Failed to write result file", true);
            }
        }

        return CommandResult.of("Done!");
    }

    protected static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    protected static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    @Override
    public String description() {
        return "Convert files to simple hex text-file";
    }

    @Override
    public String args() {
        return "<encode/decode> <source path> <result path>";
    }

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .subTree().addPossibleArg("encode")

                .recursiveSubTree()
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .parent()

                .parent()

                .subTree().addPossibleArg("decode")
                .recursiveSubTree()
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))
                .parent()
                .parent()

                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.BACKUPS;
    }
}