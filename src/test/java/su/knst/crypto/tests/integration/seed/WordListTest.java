package su.knst.crypto.tests.integration.seed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.seed.WordListCommand;

import static org.junit.jupiter.api.Assertions.*;

class WordListTest {

    static Main main;

    @BeforeAll
    static void setUp() {
        main = new Main(); // don't start because user terminal not needed
    }

    @Test
    void listsAvailableWordlists() {
        WordListCommand command = main.getHandler().getCommand(WordListCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("list"));

        assertFalse(result.error());
        assertTrue(result.message().contains("bip39_english"));
        assertTrue(result.message().contains("bip39_japanese"));
    }

    @Test
    void switchesActiveWordlistAndBack() {
        WordListCommand command = main.getHandler().getCommand(WordListCommand.class).orElseThrow();

        CommandResult switched = command.run(new ParamsContainer("set", "bip39_french"));
        assertFalse(switched.error());
        assertTrue(switched.message().contains("bip39_french"));

        CommandResult restored = command.run(new ParamsContainer("set", "bip39_english"));
        assertFalse(restored.error());
        assertTrue(restored.message().contains("bip39_english"));
    }

    @Test
    void unknownListNameIsError() {
        WordListCommand command = main.getHandler().getCommand(WordListCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("set", "not_a_real_list"));

        assertTrue(result.error());
    }

    @AfterAll
    static void afterAll() {
        // WordLists' active list is process-global state shared with every other test class,
        // so make sure we leave it as the default the rest of the suite expects.
        main.getHandler().getCommand(WordListCommand.class).orElseThrow()
                .run(new ParamsContainer("set", "bip39_english"));
    }
}
