package su.knst.crypto.command.commands.misc;

import org.jline.builtins.Completers;
import su.knst.crypto.Main;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.CommandTag;
import su.knst.crypto.utils.TerminalQuestion;
import su.knst.crypto.utils.args.ArgsTreeBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

public class DeleteCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<Path> oFile = args.stringV(0).map((p) -> Main.getCurrentPath().resolve(p));

        if (oFile.isEmpty())
            return CommandResult.error("File argument not set");

        Path target = oFile.get();

        if (!target.toFile().exists() || !target.toFile().isFile())
            return CommandResult.of("File not exists");

        Optional<Boolean> answer = Main.getTerminalWorker()
                .ask(new TerminalQuestion(
                        "Are you sure you want to delete " + target.getFileName().toString() + "?",
                        List.of("Y", "n")))
                .map((s) -> s.equals("Y"));

        if (answer.isEmpty() || !answer.get())
            return CommandResult.of("File was NOT deleted");

        // Thanks to @makkarpov for secure file delete
        try (SeekableByteChannel channel = Files.newByteChannel(target, StandardOpenOption.WRITE, StandardOpenOption.SYNC)) {
            long length = channel.size();
            long pointer = 0;
            ByteBuffer buf = ByteBuffer.allocate(1024);

            channel.position(0);

            while (pointer < length) {
                int toWrite = (int) Math.min(length - pointer, buf.capacity());
                channel.write(buf.clear().limit(toWrite));

                pointer += toWrite;
            }

            Files.delete(target);
        } catch (Exception e) {
            e.printStackTrace();

            return CommandResult.error("File was NOT deleted");
        }

        return CommandResult.of("File was deleted");
    }

    @Override
    public String description() {
        return "Override file with zeros and delete it";
    }

    @Override
    public String args() {
        return "<file>";
    }

    @Override
    public Completers.TreeCompleter.Node getArgsTree(String alias) {
        return ArgsTreeBuilder.builder().addPossibleArg(alias)
                .addCompleter(new Completers.FilesCompleter(Main::getCurrentPath))

                .build();
    }

    @Override
    public CommandTag tag() {
        return CommandTag.MISC;
    }
}
