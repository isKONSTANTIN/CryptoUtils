package su.knst.crypto.command.commands.misc;

import su.knst.crypto.cli.Ask;
import su.knst.crypto.command.Command;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.commands.CommandTag;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public class DeleteCommand extends Command {
    @Override
    public CommandResult run(Ask in) {
        Optional<Path> oFile = in.existingFile("Path to file to delete?");

        if (oFile.isEmpty())
            return CommandResult.plainError("No input");

        Path target = oFile.get();

        if (!target.toFile().exists() || !target.toFile().isFile())
            return CommandResult.plain("File not exists");

        Optional<Boolean> answer =
                in.confirm("Are you sure you want to delete " + target.getFileName() + "?");

        if (answer.isEmpty() || !answer.get())
            return CommandResult.plain("File was NOT deleted");

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

            return CommandResult.plainError("File was NOT deleted");
        }

        return CommandResult.plain("File was deleted");
    }

    @Override
    public String description() {
        return "Override file with zeros and delete it";
    }

    @Override
    public CommandTag tag() {
        return CommandTag.MISC;
    }
}
