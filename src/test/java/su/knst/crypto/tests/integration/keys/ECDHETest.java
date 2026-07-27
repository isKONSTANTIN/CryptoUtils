package su.knst.crypto.tests.integration.keys;

import org.junit.jupiter.api.*;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.keys.ECDHEKeyGeneratorCommand;
import su.knst.crypto.command.commands.seed.SeedECDHECipherCommand;
import su.knst.crypto.command.commands.seed.SeedGeneratorCommand;
import su.knst.crypto.utils.SimpleECDHE;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ECDHETest {

    static Main main;
    static String sourceEntropy;
    static String encryptedEntropy;
    static String decryptedSeedResult;
    static SecureRandom random = new SecureRandom();

    public static final Path ALICE_PUB_KEY_FILE_PATH = Path.of("test_pub_a_key");
    public static final Path ALICE_SEC_KEY_FILE_PATH = Path.of("test_sec_a_key");
    public static final Path BOB_PUB_KEY_FILE_PATH = Path.of("test_pub_b_key");
    public static final Path BOB_SEC_KEY_FILE_PATH = Path.of("test_sec_b_key");

    @BeforeAll
    static void setUp() {
        main = new Main(); // don't start because user terminal not needed

        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);

        sourceEntropy = Base64.getEncoder().encodeToString(randomBytes);
    }

    @Test
    @Order(1)
    void genKeys() throws IOException {
        ECDHEKeyGeneratorCommand keyGeneratorCommand = main.getHandler().getCommand(ECDHEKeyGeneratorCommand.class).orElseThrow();

        CommandResult aliceResult = keyGeneratorCommand.run(
                new ParamsContainer(
                        ALICE_PUB_KEY_FILE_PATH.toString(),
                        ALICE_SEC_KEY_FILE_PATH.toString()
                )
        );
        assertFalse(aliceResult.error());

        CommandResult bobResult = keyGeneratorCommand.run(
                new ParamsContainer(
                        BOB_PUB_KEY_FILE_PATH.toString(),
                        BOB_SEC_KEY_FILE_PATH.toString()
                )
        );
        assertFalse(bobResult.error());

        if (ALICE_SEC_KEY_FILE_PATH.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(ALICE_SEC_KEY_FILE_PATH)));
            assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(BOB_SEC_KEY_FILE_PATH)));
        }
    }

    @Test
    @Order(2)
    void aliceEncrypt() {
        SeedECDHECipherCommand cipherCommand = main.getHandler().getCommand(SeedECDHECipherCommand.class).orElseThrow();

        // Encrypt entropy for bob
        CommandResult aliceResult = cipherCommand.run(
                new ParamsContainer(
                        "encrypt",
                        BOB_PUB_KEY_FILE_PATH.toString(),
                        ALICE_SEC_KEY_FILE_PATH.toString(),
                        sourceEntropy
                )
        );
        assertFalse(aliceResult.error());

        String message = aliceResult.message();
        encryptedEntropy = message.substring(message.indexOf('\n') + 1); // catch from user message encrypted entropy
    }

    @Test
    @Order(3)
    void bobDecrypt() {
        SeedECDHECipherCommand cipherCommand = main.getHandler().getCommand(SeedECDHECipherCommand.class).orElseThrow();

        // Encrypt entropy for bob
        CommandResult bobResult = cipherCommand.run(
                new ParamsContainer(
                        "decrypt",
                        ALICE_PUB_KEY_FILE_PATH.toString(),
                        BOB_SEC_KEY_FILE_PATH.toString(),
                        encryptedEntropy
                )
        );
        assertFalse(bobResult.error());

        decryptedSeedResult = bobResult.message();
    }

    @Test
    @Order(4)
    void seedView() {
        SeedGeneratorCommand generatorCommand = main.getHandler().getCommand(SeedGeneratorCommand.class).orElseThrow();

        CommandResult aliceView = generatorCommand.run(new ParamsContainer(sourceEntropy));
        assertFalse(aliceView.error());

        assertEquals(decryptedSeedResult, aliceView.message());
    }

    @Test
    @Order(5)
    void ivIsNotReusedBetweenCalls() throws Exception {
        KeyPair alice = SimpleECDHE.generateECKeys();
        KeyPair bob = SimpleECDHE.generateECKeys();

        SecretKey sharedSecret = SimpleECDHE.generateSharedSecret(alice.getPrivate(), bob.getPublic());

        byte[] plaintext = "same plaintext encrypted twice".getBytes();

        byte[] firstCipherText = SimpleECDHE.encrypt(sharedSecret, plaintext);
        byte[] secondCipherText = SimpleECDHE.encrypt(sharedSecret, plaintext);

        int ivLength = firstCipherText[0];
        byte[] firstIv = Arrays.copyOfRange(firstCipherText, 1, 1 + ivLength);
        byte[] secondIv = Arrays.copyOfRange(secondCipherText, 1, 1 + ivLength);

        assertFalse(Arrays.equals(firstIv, secondIv), "IV must not be reused between encrypt() calls");

        assertArrayEquals(plaintext, SimpleECDHE.decrypt(sharedSecret, firstCipherText));
        assertArrayEquals(plaintext, SimpleECDHE.decrypt(sharedSecret, secondCipherText));
    }

    @AfterAll
    static void afterAll() throws IOException {
        Files.delete(ALICE_PUB_KEY_FILE_PATH);
        Files.delete(ALICE_SEC_KEY_FILE_PATH);

        Files.delete(BOB_PUB_KEY_FILE_PATH);
        Files.delete(BOB_SEC_KEY_FILE_PATH);
    }
}