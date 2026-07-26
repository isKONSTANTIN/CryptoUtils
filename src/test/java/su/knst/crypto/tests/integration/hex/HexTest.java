package su.knst.crypto.tests.integration.hex;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.hex.HexCommand;
import su.knst.crypto.utils.HexUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class HexTest {

    static Main main;
    static SecureRandom random = new SecureRandom();

    public static final Path SOURCE_FILE_PATH = Path.of("test_hex_source");
    public static final Path ENCODED_FILE_PATH = Path.of("test_hex_encoded");
    public static final Path DECODED_FILE_PATH = Path.of("test_hex_decoded");

    @BeforeAll
    static void setUp() throws IOException {
        main = new Main(); // don't start because user terminal not needed

        byte[] sourceBytes = new byte[256];
        random.nextBytes(sourceBytes);

        Files.write(SOURCE_FILE_PATH, sourceBytes);
    }

    @Test
    void encodeThenDecodeRoundTrips() throws IOException {
        HexCommand command = main.getHandler().getCommand(HexCommand.class).orElseThrow();

        CommandResult encodeResult = command.run(new ParamsContainer(
                "encode", SOURCE_FILE_PATH.toString(), ENCODED_FILE_PATH.toString()
        ));
        assertFalse(encodeResult.error());

        byte[] sourceBytes = Files.readAllBytes(SOURCE_FILE_PATH);
        assertEquals(HexUtils.bytesToHex(sourceBytes), Files.readString(ENCODED_FILE_PATH));

        CommandResult decodeResult = command.run(new ParamsContainer(
                "decode", ENCODED_FILE_PATH.toString(), DECODED_FILE_PATH.toString()
        ));
        assertFalse(decodeResult.error());

        assertArrayEquals(sourceBytes, Files.readAllBytes(DECODED_FILE_PATH));
    }

    @Test
    void wrongModeIsError() {
        HexCommand command = main.getHandler().getCommand(HexCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer(
                "wipe", SOURCE_FILE_PATH.toString(), ENCODED_FILE_PATH.toString()
        ));

        assertTrue(result.error());
    }

    @Test
    void missingArgsIsError() {
        HexCommand command = main.getHandler().getCommand(HexCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("encode"));

        assertTrue(result.error());
    }

    @AfterAll
    static void afterAll() throws IOException {
        Files.delete(SOURCE_FILE_PATH);
        Files.delete(ENCODED_FILE_PATH);
        Files.delete(DECODED_FILE_PATH);
    }
}
