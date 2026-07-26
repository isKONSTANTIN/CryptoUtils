package su.knst.crypto.tests.integration.keys;

import org.junit.jupiter.api.*;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.keys.RSAKeyGeneratorCommand;
import su.knst.crypto.command.commands.seed.SeedGeneratorCommand;
import su.knst.crypto.command.commands.seed.SeedRSACipherCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RSATest {

    static Main main;
    static String sourceEntropy;
    static String encryptedEntropy;
    static String decryptedSeedResult;
    static SecureRandom random = new SecureRandom();

    public static final Path PUB_KEY_FILE_PATH = Path.of("test_rsa_pub_key");
    public static final Path SEC_KEY_FILE_PATH = Path.of("test_rsa_sec_key");

    @BeforeAll
    static void setUp() {
        main = new Main(); // don't start because user terminal not needed

        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);

        sourceEntropy = Base64.getEncoder().encodeToString(randomBytes);
    }

    @Test
    @Order(1)
    void genKeys() {
        RSAKeyGeneratorCommand keyGeneratorCommand = main.getHandler().getCommand(RSAKeyGeneratorCommand.class).orElseThrow();

        CommandResult result = keyGeneratorCommand.run(
                new ParamsContainer(
                        PUB_KEY_FILE_PATH.toString(),
                        SEC_KEY_FILE_PATH.toString()
                )
        );

        assertFalse(result.error());
    }

    @Test
    @Order(2)
    void encrypt() throws IOException {
        SeedRSACipherCommand cipherCommand = main.getHandler().getCommand(SeedRSACipherCommand.class).orElseThrow();

        CommandResult result = cipherCommand.run(
                true,
                Files.readAllBytes(PUB_KEY_FILE_PATH),
                Base64.getDecoder().decode(sourceEntropy)
        );

        assertFalse(result.error());

        String message = result.message();
        encryptedEntropy = message.substring(message.indexOf('\n') + 1); // catch from user message encrypted entropy
    }

    @Test
    @Order(3)
    void decrypt() throws IOException {
        SeedRSACipherCommand cipherCommand = main.getHandler().getCommand(SeedRSACipherCommand.class).orElseThrow();

        CommandResult result = cipherCommand.run(
                false,
                Files.readAllBytes(SEC_KEY_FILE_PATH),
                Base64.getDecoder().decode(encryptedEntropy)
        );

        assertFalse(result.error());

        decryptedSeedResult = result.message();
    }

    @Test
    @Order(4)
    void seedView() {
        SeedGeneratorCommand generatorCommand = main.getHandler().getCommand(SeedGeneratorCommand.class).orElseThrow();

        CommandResult view = generatorCommand.run(new ParamsContainer(sourceEntropy));
        assertFalse(view.error());

        assertEquals(decryptedSeedResult, view.message());
    }

    @AfterAll
    static void afterAll() throws IOException {
        Files.delete(PUB_KEY_FILE_PATH);
        Files.delete(SEC_KEY_FILE_PATH);
    }
}
