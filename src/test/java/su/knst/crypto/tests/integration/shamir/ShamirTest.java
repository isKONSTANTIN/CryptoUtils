package su.knst.crypto.tests.integration.shamir;

import org.junit.jupiter.api.*;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.shamir.ShamirCommand;
import su.knst.crypto.utils.HexUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static su.knst.crypto.utils.HexUtils.hexHash;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShamirTest {

    static Main main;

    public static final int ALL_PARTS = 5;
    public static final int PARTS_FOR_RECOVER = 3;

    public static final Path TEST_FILE_PATH = Path.of("test_file");
    public static final Path TEST_JOINED_FILE_PATH = Path.of("test_file_joined");

    static String testFileHash;

    static SecureRandom random = new SecureRandom();

    @BeforeAll
    static void setUp() throws IOException, NoSuchAlgorithmException {
        main = new Main(); // don't start because user terminal not needed

        byte[] randomBytes = new byte[1024 * 2];
        random.nextBytes(randomBytes);

        Files.write(TEST_FILE_PATH, randomBytes);

        testFileHash = hexHash(randomBytes);
    }

    // Just split file of random bytes
    @Test
    @Order(1)
    void split() {
        ShamirCommand shamirCommand = main.getHandler().getCommand(ShamirCommand.class).orElseThrow();

        CommandResult result = shamirCommand.run(
                new ParamsContainer(
                        "split",
                        String.valueOf(ALL_PARTS),
                        String.valueOf(PARTS_FOR_RECOVER),
                        TEST_FILE_PATH.toString()
                )
        );

        assertFalse(result.error());
    }

    // Here join random files.
    @Order(2)
    @RepeatedTest(5)
    void join() throws IOException, NoSuchAlgorithmException {
        ShamirCommand shamirCommand = main.getHandler().getCommand(ShamirCommand.class).orElseThrow();

        // setup files for joining.
        ArrayList<String> joinFilesArgs = new ArrayList<>(ALL_PARTS - PARTS_FOR_RECOVER);
        IntStream.range(0, PARTS_FOR_RECOVER).forEach(i -> joinFilesArgs.add(""));
        IntStream.range(0, ALL_PARTS - PARTS_FOR_RECOVER).forEach(i -> joinFilesArgs.add("null"));

        Collections.shuffle(joinFilesArgs, random); // random files

        for (int i = 0; i < joinFilesArgs.size(); i++) {
            if (joinFilesArgs.get(i).isEmpty()) // if file is not "null", putting correct name with index
                joinFilesArgs.set(i, TEST_FILE_PATH + ".shp-" + (i + 1));
        }

        ArrayList<String> args = new ArrayList<>(List.of("join", TEST_JOINED_FILE_PATH.toString()));
        args.addAll(joinFilesArgs);

        CommandResult result = shamirCommand.run(new ParamsContainer(args));

        assertFalse(result.error());
        assertEquals(testFileHash, hexHash(Files.readAllBytes(TEST_JOINED_FILE_PATH)));
    }

    @AfterAll
    static void afterAll() throws IOException {
        Files.delete(TEST_FILE_PATH);
        Files.delete(TEST_JOINED_FILE_PATH);

        for(int i = 0; i < ALL_PARTS; i++)
            Files.delete(Path.of(TEST_FILE_PATH + ".shp-" + (i + 1)));
    }
}