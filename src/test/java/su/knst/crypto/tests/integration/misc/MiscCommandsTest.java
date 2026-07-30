package su.knst.crypto.tests.integration.misc;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.cli.Ask;
import su.knst.crypto.tests.cli.ScriptedQuestioner;
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

        CommandResult result = command.run(ask());

        assertFalse(result.error());

        for (String alias : EXPECTED_ALIASES)
            assertTrue(result.message().contains(alias), "help must list '" + alias + "'");
    }

    @Test
    void exactlyTheExpectedCommandsAreRegistered() {
        assertEquals(EXPECTED_ALIASES, main.getHandler().getCommands().keySet());
    }

    @Test
    void changeDirectoryWithNoAnswerIsVoid() {
        ChangeDirectoryCommand command = main.getHandler().getCommand(ChangeDirectoryCommand.class).orElseThrow();

        CommandResult result = command.run(ask());

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

        // existingFile() re-asks until a real file is named, so a missing one just cancels
        CommandResult result = command.run(ask("this_file_does_not_exist_hopefully"));

        assertTrue(result.error());
    }

    @Test
    void deleteWithNoAnswerIsError() {
        DeleteCommand command = main.getHandler().getCommand(DeleteCommand.class).orElseThrow();

        CommandResult result = command.run(ask());

        assertTrue(result.error());
    }

    @Test
    void exitCommandShutsDownWithoutError() {
        ExitCommand command = main.getHandler().getCommand(ExitCommand.class).orElseThrow();

        CommandResult result = command.run(ask());

        assertEquals(CommandResult.VOID, result);
    }

    static Ask ask(String... answers) {
        return new Ask(new ScriptedQuestioner(answers));
    }
}
