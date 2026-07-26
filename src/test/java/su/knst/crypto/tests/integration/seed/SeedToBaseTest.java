package su.knst.crypto.tests.integration.seed;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.seed.SeedToBaseCommand;
import su.knst.crypto.utils.MnemonicUtils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SeedToBaseTest {

    static Main main;
    static SecureRandom random = new SecureRandom();

    @BeforeAll
    static void setUp() {
        main = new Main(); // don't start because user terminal not needed
    }

    @Test
    void roundTripsEntropyThroughMnemonic() throws NoSuchAlgorithmException {
        byte[] entropy = new byte[16];
        random.nextBytes(entropy);

        String[] mnemonic = MnemonicUtils.createMnemonic(entropy);

        SeedToBaseCommand command = main.getHandler().getCommand(SeedToBaseCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer(mnemonic));

        assertFalse(result.error());

        String message = result.message();
        String prefix = "Base64 encoded: ";
        String base64 = message.substring(message.indexOf(prefix) + prefix.length()).trim();

        assertArrayEquals(entropy, Base64.getDecoder().decode(base64));
    }

    @Test
    void wordNotInDictionaryIsError() {
        SeedToBaseCommand command = main.getHandler().getCommand(SeedToBaseCommand.class).orElseThrow();

        String[] mnemonic = new String[12];
        Arrays.fill(mnemonic, "abandon");
        mnemonic[0] = "notarealbip39word";

        CommandResult result = command.run(new ParamsContainer(mnemonic));

        assertTrue(result.error());
    }
}
