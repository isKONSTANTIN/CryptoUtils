package su.knst.crypto.tests.integration.backup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.backup.CardCommand;
import su.knst.crypto.command.commands.qr.CodeCommand;
import su.knst.crypto.utils.HexUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test of a single share card the way a real user produces and later recovers it:
 * {@code card} renders one share as a printable PNG with an embedded QR code (no Shamir split
 * involved, unlike {@link BackupWorkflowE2ETest}), and recovery happens by photographing/scanning
 * that QR code with {@code qr scan} - exactly what a phone camera app or this CLI's own scanner
 * would do - and hex-decoding the recovered payload back to the original bytes.
 */
class CardCommandE2ETest {

    Main main;
    static SecureRandom random = new SecureRandom();
    List<Path> filesToCleanUp;

    @BeforeEach
    void setUp() {
        main = new Main(); // don't start because user terminal not needed
        filesToCleanUp = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Path path : filesToCleanUp)
            Files.deleteIfExists(path);
    }

    private CommandResult createCard(String type, String name, int shareIndex, int totalShares, int threshold, List<String> source) {
        CardCommand card = main.getHandler().getCommand(CardCommand.class).orElseThrow();

        List<String> args = new ArrayList<>(List.of(type, name, String.valueOf(shareIndex), String.valueOf(totalShares), String.valueOf(threshold)));
        args.addAll(source);

        return card.run(new ParamsContainer(args));
    }

    private String scanQr(Path cardPath) {
        CodeCommand qr = main.getHandler().getCommand("qr", CodeCommand.class).orElseThrow();

        CommandResult scanResult = qr.run(new ParamsContainer("scan", cardPath.toString()));
        assertFalse(scanResult.error(), "qr scan failed: " + scanResult.message());

        return scanResult.message();
    }

    @Test
    void textCardRoundTripsThroughQrScan() {
        String text = "the quick brown fox backs up its secret";
        String name = "e2e_card_text";
        int shareIndex = 2, totalShares = 5, threshold = 3;

        Path cardPath = Path.of(name + "_" + shareIndex + ".png");
        filesToCleanUp.add(cardPath);

        CommandResult createResult = createCard("text", name, shareIndex, totalShares, threshold, List.of(text));
        assertFalse(createResult.error(), createResult.message());
        assertTrue(Files.exists(cardPath));
        assertTrue(createResult.message().contains(shareIndex + "/" + totalShares));

        String decodedHex = scanQr(cardPath);
        byte[] recovered = HexUtils.hexStringToByteArray(decodedHex);

        assertEquals(text, new String(recovered, StandardCharsets.UTF_8));
    }

    @Test
    void hexCardRoundTripsThroughQrScan() {
        byte[] payload = new byte[32];
        random.nextBytes(payload);
        String hex = HexUtils.bytesToHex(payload);

        String name = "e2e_card_hex";
        int shareIndex = 1, totalShares = 3, threshold = 2;

        Path cardPath = Path.of(name + "_" + shareIndex + ".png");
        filesToCleanUp.add(cardPath);

        CommandResult createResult = createCard("hex", name, shareIndex, totalShares, threshold, List.of(hex));
        assertFalse(createResult.error(), createResult.message());

        String decodedHex = scanQr(cardPath);

        // hex type keeps the QR payload byte-identical to what was typed - no re-encoding round trip
        assertEquals(hex.toLowerCase(), decodedHex.toLowerCase());
    }

    @Test
    void fileCardRoundTripsThroughQrScan() throws IOException {
        byte[] content = new byte[48];
        random.nextBytes(content);

        Path source = Path.of("e2e_card_source_file");
        filesToCleanUp.add(source);
        Files.write(source, content);

        String name = "e2e_card_file";
        int shareIndex = 4, totalShares = 7, threshold = 4;

        Path cardPath = Path.of(name + "_" + shareIndex + ".png");
        filesToCleanUp.add(cardPath);

        CommandResult createResult = createCard("file", name, shareIndex, totalShares, threshold, List.of(source.toString()));
        assertFalse(createResult.error(), createResult.message());

        String decodedHex = scanQr(cardPath);
        byte[] recovered = HexUtils.hexStringToByteArray(decodedHex);

        assertArrayEquals(content, recovered);
    }
}
