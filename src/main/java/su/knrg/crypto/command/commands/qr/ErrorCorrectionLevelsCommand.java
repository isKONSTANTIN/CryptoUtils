package su.knrg.crypto.command.commands.qr;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.command.commands.CommandTag;

import java.util.Arrays;

public class ErrorCorrectionLevelsCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        StringBuilder builder = new StringBuilder();

        builder.append("All correction levels formats:").append('\n');

        Arrays.stream(ErrorCorrectionLevel.values())
                .map(v -> v.name().toLowerCase())
                .forEach(s -> builder.append(s).append('\n'));

        builder.deleteCharAt(builder.length() - 1);

        return CommandResult.of(builder.toString());
    }

    @Override
    public String description() {
        return "Show available error correction levels for qr command";
    }

    @Override
    public String args() {
        return null;
    }

    @Override
    public CommandTag tag() {
        return CommandTag.BACKUPS;
    }
}
