package su.knst.crypto.tests.integration.seed;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.seed.HexToSeedCommand;
import su.knst.crypto.command.commands.seed.SeedToHexCommand;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.MnemonicUtils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class HexToSeedTest {

    static Main main;
    static SecureRandom random = new SecureRandom();

    @BeforeAll
    static void setUp() {
        main = new Main(); // don't start because user terminal not needed
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 32})
    void generatesSeedFromGivenHexEntropy(int entropyBytes) {
        HexToSeedCommand command = main.getHandler().getCommand(HexToSeedCommand.class).orElseThrow();

        byte[] entropy = new byte[entropyBytes];
        random.nextBytes(entropy);

        String hex = HexUtils.bytesToHex(entropy);

        CommandResult result = command.run(new ParamsContainer(hex));

        assertFalse(result.error());
        assertTrue(result.message().contains(hex));

        if (entropyBytes >= 32)
            assertTrue(result.message().contains("24-word seed"));

        assertTrue(result.message().contains("12-word seed"));
    }

    @Test
    void isReverseOfSeedToHex() throws NoSuchAlgorithmException {
        byte[] entropy = new byte[16];
        random.nextBytes(entropy);

        String[] mnemonic = MnemonicUtils.createMnemonic(entropy);

        SeedToHexCommand toHex = main.getHandler().getCommand(SeedToHexCommand.class).orElseThrow();
        CommandResult toHexResult = toHex.run(new ParamsContainer(mnemonic));

        String message = toHexResult.message();
        String prefix = "Hex encoded: ";
        String hex = message.substring(message.indexOf(prefix) + prefix.length()).trim();

        HexToSeedCommand toSeed = main.getHandler().getCommand(HexToSeedCommand.class).orElseThrow();
        CommandResult toSeedResult = toSeed.run(new ParamsContainer(hex));

        assertFalse(toSeedResult.error());
        for (String word : mnemonic)
            assertTrue(toSeedResult.message().contains(word));
    }

    @Test
    void acceptsLowercaseAndUppercaseHex() {
        HexToSeedCommand command = main.getHandler().getCommand(HexToSeedCommand.class).orElseThrow();

        byte[] entropy = new byte[16];
        random.nextBytes(entropy);

        String upper = HexUtils.bytesToHex(entropy);
        String lower = upper.toLowerCase();

        CommandResult upperResult = command.run(new ParamsContainer(upper));
        CommandResult lowerResult = command.run(new ParamsContainer(lower));

        assertFalse(upperResult.error());
        assertFalse(lowerResult.error());
        assertEquals(upperResult.message(), lowerResult.message());
    }

    @Test
    void tooShortEntropyIsError() {
        HexToSeedCommand command = main.getHandler().getCommand(HexToSeedCommand.class).orElseThrow();

        byte[] entropy = new byte[8]; // less than min 16 bytes
        random.nextBytes(entropy);

        CommandResult result = command.run(new ParamsContainer(HexUtils.bytesToHex(entropy)));

        assertTrue(result.error());
    }

    @Test
    void oddLengthHexIsError() {
        HexToSeedCommand command = main.getHandler().getCommand(HexToSeedCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("abc"));

        assertTrue(result.error());
    }

    @Test
    void nonHexCharactersIsError() {
        HexToSeedCommand command = main.getHandler().getCommand(HexToSeedCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("zzzznotvalidhexnotvalidhex0000000000000000000000000000000000"));

        assertTrue(result.error());
    }

    @Test
    void noArgsIsError() {
        HexToSeedCommand command = main.getHandler().getCommand(HexToSeedCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer());

        assertTrue(result.error());
    }
}
