package su.knst.crypto.tests.integration.misc;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.misc.ChangeDirectoryCommand;
import su.knst.crypto.command.commands.misc.DeleteCommand;
import su.knst.crypto.command.commands.misc.ExitCommand;
import su.knst.crypto.command.commands.misc.HelpCommand;

import java.nio.file.Path;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MiscCommandsTest {

    // v2 deliberately ships a small command set: everything else was folded into these or dropped.
    static final Set<String> EXPECTED_ALIASES =
            Set.of("help", "exit", "q", "cd", "delete", "seed", "backup", "restore");

    static Main main;

    @BeforeAll
    static void setUp() {
        main = new Main(); // don't start because user terminal not needed
    }

    @Test
    void helpListsAllRegisteredCommands() {
        HelpCommand command = main.getHandler().getCommand(HelpCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer());

        assertFalse(result.error());

        for (String alias : EXPECTED_ALIASES)
            assertTrue(result.message().contains(alias), "help must list '" + alias + "'");
    }

    @Test
    void exactlyTheExpectedCommandsAreRegistered() {
        assertEquals(EXPECTED_ALIASES, main.getHandler().getCommands().keySet());
    }

    @Test
    void changeDirectoryWithNoArgIsVoid() {
        ChangeDirectoryCommand command = main.getHandler().getCommand(ChangeDirectoryCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer());

        assertEquals(CommandResult.VOID, result);
    }

    @Test
    void changeDirectoryIgnoresNonExistingPath() {
        Path before = Main.getCurrentPath();

        Main.changeCurrentPath("this_directory_does_not_exist_hopefully");

        assertEquals(before, Main.getCurrentPath());
    }

    @Test
    void deleteMissingFileDoesNothing() {
        DeleteCommand command = main.getHandler().getCommand(DeleteCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("this_file_does_not_exist_hopefully"));

        assertFalse(result.error());
        assertEquals("File not exists", result.message());
    }

    @Test
    void deleteWithNoArgIsError() {
        DeleteCommand command = main.getHandler().getCommand(DeleteCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer());

        assertTrue(result.error());
    }

    @Test
    void exitCommandShutsDownWithoutError() {
        ExitCommand command = main.getHandler().getCommand(ExitCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer());

        assertEquals(CommandResult.VOID, result);
    }
}
