package su.knrg.crypto.command.commands.qr;

import com.google.zxing.NotFoundException;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import su.knrg.crypto.command.Command;
import su.knrg.crypto.command.CommandResult;
import su.knrg.crypto.command.ParamsContainer;
import su.knrg.crypto.utils.SimpleECDHE;
import su.knrg.crypto.utils.SimpleFileWorker;
import su.knrg.crypto.utils.SimpleQRCodeWorker;

import java.io.IOException;
import java.security.KeyPair;
import java.util.Optional;

public class QRCodeCommand extends Command {
    @Override
    public CommandResult run(ParamsContainer args) {
        Optional<String> oMode = args.stringV(0);

        if (oMode.isEmpty())
            return CommandResult.of("Mode not set", true);

        boolean mode = oMode.map((s) -> s.equals("scan")).get();

        if (mode) {
            Optional<String> oQRCodePath = args.stringV(1);

            if (oQRCodePath.isEmpty())
                return CommandResult.of("QR code path not set", true);

            Optional<String> oResultPath = args.stringV(2);

            String result;
            try {
                result = SimpleQRCodeWorker.readQRCode(oQRCodePath.get());
            } catch (IOException | NotFoundException e) {
                e.printStackTrace();

                return CommandResult.of("Fail to scan", true);
            }

            if (oResultPath.isPresent()) {
                try {
                    SimpleFileWorker.of(oResultPath.get()).writeToFile(result);
                } catch (IOException e) {
                    e.printStackTrace();

                    return CommandResult.of("Fail to write result", true);
                }
            }else {
                return CommandResult.of(result);
            }
        }else {
            Optional<String> oQRCodePath = args.stringV(1);

            if (oQRCodePath.isEmpty())
                return CommandResult.of("QR code path not set", true);

            Optional<Integer> oPixels = args.stringV(2).map(Integer::valueOf);

            if (oPixels.isEmpty())
                return CommandResult.of("Pixels width not set", true);

            Optional<String> oData = args.stringV(3);

            if (oData.isEmpty())
                return CommandResult.of("Text or path not set", true);

            StringBuilder data = new StringBuilder(oData.get());

            try {
                if (data.toString().startsWith("f:")){
                    String path = data.substring(2);

                    generateFromFile(oQRCodePath.get(), path, oPixels.get());
                }else {
                    for (int i = 4; i < args.size(); i++)
                        data.append(" ").append(args.stringV(i).get());

                    generate(oQRCodePath.get(), data.toString(), oPixels.get());
                }
            } catch (Exception e) {
                e.printStackTrace();

                return CommandResult.of("Failed", true);
            }
        }

        return CommandResult.of("Done");
    }

    public void generate(String resultPath, String text, int pixelsWidth) throws IOException, WriterException {
        SimpleQRCodeWorker.generateQRCode(text, resultPath, pixelsWidth, pixelsWidth, ErrorCorrectionLevel.Q);
    }

    public void generateFromFile(String resultPath, String sourcePath, int pixelsWidth) throws IOException, WriterException {
        SimpleQRCodeWorker.generateQRCode(
                SimpleFileWorker.of(sourcePath).readFromFile(),
                resultPath,
                pixelsWidth,
                pixelsWidth,
                ErrorCorrectionLevel.Q
        );
    }

    @Override
    public String description() {
        return "Scan or generate QR Code";
    }

    @Override
    public String args() {
        return "scan <QR code path> [result path] | generate <result path> <pixels width> <source text/f:path>";
    }
}
