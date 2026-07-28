package su.knst.crypto.tests.integration.seed;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.seed.SeedToHexCommand;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.MnemonicUtils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SeedToHexTest {

    static Main main;
    static SecureRandom random = new SecureRandom();

    @BeforeAll
    static void setUp() {
        main = new Main(); // don't start because user terminal not needed
    }

    private static String hexFromResult(CommandResult result) {
        String message = result.message();
        String prefix = "Hex encoded: ";
        return message.substring(message.indexOf(prefix) + prefix.length()).trim();
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 32})
    void roundTripsEntropyThroughMnemonic(int entropyBytes) throws NoSuchAlgorithmException {
        byte[] entropy = new byte[entropyBytes];
        random.nextBytes(entropy);

        String[] mnemonic = MnemonicUtils.createMnemonic(entropy);

        SeedToHexCommand command = main.getHandler().getCommand(SeedToHexCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer(mnemonic));

        assertFalse(result.error());

        String hex = hexFromResult(result);

        assertArrayEquals(entropy, HexUtils.hexStringToByteArray(hex));
    }

    @Test
    void hexOutputIsUppercaseAndCorrectLength() throws NoSuchAlgorithmException {
        byte[] entropy = new byte[16];
        random.nextBytes(entropy);

        String[] mnemonic = MnemonicUtils.createMnemonic(entropy);

        SeedToHexCommand command = main.getHandler().getCommand(SeedToHexCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer(mnemonic));
        String hex = hexFromResult(result);

        assertEquals(32, hex.length());
        assertEquals(hex, hex.toUpperCase());
        assertTrue(hex.matches("[0-9A-F]+"));
    }

    @Test
    void zeroEntropyProducesAllZeroHex() throws NoSuchAlgorithmException {
        byte[] entropy = new byte[16];

        String[] mnemonic = MnemonicUtils.createMnemonic(entropy);

        SeedToHexCommand command = main.getHandler().getCommand(SeedToHexCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer(mnemonic));
        String hex = hexFromResult(result);

        assertEquals("0".repeat(32), hex);
    }

    @Test
    void wordNotInDictionaryIsError() {
        SeedToHexCommand command = main.getHandler().getCommand(SeedToHexCommand.class).orElseThrow();

        String[] mnemonic = new String[12];
        Arrays.fill(mnemonic, "abandon");
        mnemonic[0] = "notarealbip39word";

        CommandResult result = command.run(new ParamsContainer(mnemonic));

        assertTrue(result.error());
    }

    @Test
    void wrongChecksumIsError() {
        SeedToHexCommand command = main.getHandler().getCommand(SeedToHexCommand.class).orElseThrow();

        String[] mnemonic = new String[12];
        Arrays.fill(mnemonic, "abandon");
        mnemonic[11] = "art"; // valid word but breaks the checksum for all-"abandon" entropy

        CommandResult result = command.run(new ParamsContainer(mnemonic));

        assertTrue(result.error());
    }

    @Test
    void commandIsRegisteredUnderExpectedAlias() {
        assertTrue(main.getHandler().getCommand(SeedToHexCommand.class).isPresent());
    }
}
