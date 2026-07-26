package su.knst.crypto.tests.integration.seed;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.seed.SeedExtenderCommand;
import su.knst.crypto.utils.MnemonicUtils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class SeedExtenderTest {

    static Main main;
    static SecureRandom random = new SecureRandom();

    @BeforeAll
    static void setUp() {
        main = new Main(); // don't start because user terminal not needed
    }

    @Test
    void extendsValid12WordSeedTo24Words() throws NoSuchAlgorithmException {
        byte[] entropy = new byte[16];
        random.nextBytes(entropy);

        String[] mnemonic = MnemonicUtils.createMnemonic(entropy);
        assertEquals(12, mnemonic.length);

        SeedExtenderCommand command = main.getHandler().getCommand(SeedExtenderCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer(mnemonic));

        assertFalse(result.error());
        assertTrue(result.message().contains("24-word seed"));
    }

    @Test
    void wrongWordCountIsError() {
        SeedExtenderCommand command = main.getHandler().getCommand(SeedExtenderCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("word1", "word2"));

        assertTrue(result.error());
    }

    @Test
    void wordNotInDictionaryIsRejected() throws NoSuchAlgorithmException {
        byte[] entropy = new byte[16];
        random.nextBytes(entropy);

        String[] mnemonic = MnemonicUtils.createMnemonic(entropy);
        mnemonic[0] = "notarealbip39word";

        SeedExtenderCommand command = main.getHandler().getCommand(SeedExtenderCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer(mnemonic));

        assertTrue(result.message().contains("Failed to check mnemonic"));
    }
}
