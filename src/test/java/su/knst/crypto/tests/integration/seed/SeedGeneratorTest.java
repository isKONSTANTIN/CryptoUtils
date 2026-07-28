package su.knst.crypto.tests.integration.seed;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.seed.SeedGeneratorCommand;
import su.knst.crypto.utils.HexUtils;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SeedGeneratorTest {

    static Main main;
    static SecureRandom random = new SecureRandom();

    @BeforeAll
    static void setUp() {
        main = new Main(); // don't start because user terminal not needed
    }

    @Test
    void generatesFromGivenEntropy() {
        SeedGeneratorCommand command = main.getHandler().getCommand(SeedGeneratorCommand.class).orElseThrow();

        byte[] entropy = new byte[32];
        random.nextBytes(entropy);

        String base64 = Base64.getEncoder().encodeToString(entropy);

        CommandResult result = command.run(new ParamsContainer(base64));

        assertFalse(result.error());
        assertTrue(result.message().contains(base64));
        assertTrue(result.message().contains(HexUtils.bytesToHex(entropy)));
        assertTrue(result.message().contains("24-word seed"));
        assertTrue(result.message().contains("12-word seed"));
    }

    @Test
    void generatesRandomEntropyWhenNoArgsGiven() {
        SeedGeneratorCommand command = main.getHandler().getCommand(SeedGeneratorCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer());

        assertFalse(result.error());
        assertTrue(result.message().contains("24-word seed"));
    }

    @Test
    void tooShortEntropyIsError() {
        SeedGeneratorCommand command = main.getHandler().getCommand(SeedGeneratorCommand.class).orElseThrow();

        byte[] entropy = new byte[8]; // less than min 16 bytes
        random.nextBytes(entropy);

        CommandResult result = command.run(new ParamsContainer(Base64.getEncoder().encodeToString(entropy)));

        assertTrue(result.error());
    }

    @Test
    void invalidBase64IsError() {
        SeedGeneratorCommand command = main.getHandler().getCommand(SeedGeneratorCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("not-valid-base64!!"));

        assertTrue(result.error());
    }
}
