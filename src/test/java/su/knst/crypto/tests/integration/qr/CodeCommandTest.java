package su.knst.crypto.tests.integration.qr;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.qr.CodeCommand;
import su.knst.crypto.command.commands.qr.ErrorCorrectionLevelsCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CodeCommandTest {

    static Main main;

    public static final Path QR_FILE_PATH = Path.of("test_qr_code.png");
    public static final Path PDF417_FILE_PATH = Path.of("test_pdf417_code.png");

    @BeforeAll
    static void setUp() {
        main = new Main(); // don't start because user terminal not needed
    }

    @Test
    void qrGenerateThenScanRoundTrips() {
        CodeCommand command = main.getHandler().getCommand("qr", CodeCommand.class).orElseThrow();

        CommandResult generateResult = command.run(new ParamsContainer(
                "generate", QR_FILE_PATH.toString(), "300", "Hello", "CryptoUtils"
        ));
        assertFalse(generateResult.error());
        assertTrue(Files.exists(QR_FILE_PATH));

        CommandResult scanResult = command.run(new ParamsContainer("scan", QR_FILE_PATH.toString()));
        assertFalse(scanResult.error());
        assertEquals("Hello CryptoUtils", scanResult.message());
    }

    @Test
    void pdf417GenerateThenScanRoundTrips() {
        CodeCommand command = main.getHandler().getCommand("pdf417", CodeCommand.class).orElseThrow();

        CommandResult generateResult = command.run(new ParamsContainer(
                "generate", PDF417_FILE_PATH.toString(), "300", "Hello", "PDF417"
        ));
        assertFalse(generateResult.error());
        assertTrue(Files.exists(PDF417_FILE_PATH));

        CommandResult scanResult = command.run(new ParamsContainer("scan", PDF417_FILE_PATH.toString()));
        assertFalse(scanResult.error());
        assertEquals("Hello PDF417", scanResult.message());
    }

    @Test
    void wrongModeIsError() {
        CodeCommand command = main.getHandler().getCommand("qr", CodeCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("fly", QR_FILE_PATH.toString()));

        assertTrue(result.error());
    }

    @Test
    void listsErrorCorrectionLevels() {
        ErrorCorrectionLevelsCommand command = main.getHandler().getCommand(ErrorCorrectionLevelsCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer());

        assertFalse(result.error());
        assertTrue(result.message().contains("l"));
        assertTrue(result.message().contains("h"));
    }

    @AfterAll
    static void afterAll() throws IOException {
        Files.delete(QR_FILE_PATH);
        Files.delete(PDF417_FILE_PATH);
    }
}
