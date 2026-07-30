package su.knst.crypto.tests.integration.backup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.backup.BackupCreateCommand;
import su.knst.crypto.command.commands.backup.BackupRestoreCommand;
import su.knst.crypto.tests.util.BackupTestFiles;
import su.knst.crypto.utils.MnemonicUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end round trip through only the two new Command objects (backup -> restore),
 * for each of the three source types, the same way SeedBackupWorkflowTest chains the
 * existing seed/hex/shamir commands.
 */
class BackupWorkflowE2ETest {

    static Main main;
    static SecureRandom random = new SecureRandom();
    List<Path> filesToCleanUp;

    @BeforeEach
    void setUp() {
        main = new Main(); // don't start because user terminal not needed
        filesToCleanUp = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Path path : filesToCleanUp)
            Files.deleteIfExists(path);
    }

    @Test
    void fullFileWorkflow() throws Exception {
        byte[] content = new byte[128];
        random.nextBytes(content);

        Path source = Path.of("e2e_backup_source_file");
        filesToCleanUp.add(source);
        Files.write(source, content);

        String name = "e2e_backup_file";
        int n = 5, k = 3;

        BackupCreateCommand create = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();
        BackupRestoreCommand restore = main.getHandler().getCommand(BackupRestoreCommand.class).orElseThrow();

        for (int i = 1; i <= n; i++)
            filesToCleanUp.add(Path.of(name + "_" + i + ".png"));

        CommandResult createResult = create.run(new ParamsContainer("file", name, "null", "split", String.valueOf(n), String.valueOf(k), source.toString()));
        assertFalse(createResult.error(), createResult.message());
        filesToCleanUp.addAll(BackupTestFiles.printSheetPaths(name));

        Path output = Path.of("e2e_backup_file_restored");
        filesToCleanUp.add(output);

        List<String> args = new ArrayList<>(List.of("file", output.toString(), "shamir"));
        for (int i = 1; i <= n; i++)
            args.add(i <= k ? name + "_" + i + ".png" : "null");

        CommandResult restoreResult = restore.run(new ParamsContainer(args));
        assertFalse(restoreResult.error(), restoreResult.message());

        assertArrayEquals(content, Files.readAllBytes(output));
    }

    @Test
    void fullTextWorkflow() throws Exception {
        String text = "the quick brown fox backs up its secret";
        String name = "e2e_backup_text";
        int n = 4, k = 2;

        BackupCreateCommand create = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();
        BackupRestoreCommand restore = main.getHandler().getCommand(BackupRestoreCommand.class).orElseThrow();

        for (int i = 1; i <= n; i++)
            filesToCleanUp.add(Path.of(name + "_" + i + ".png"));

        List<String> createArgs = new ArrayList<>(List.of("text", name, "null", "split", String.valueOf(n), String.valueOf(k)));
        createArgs.addAll(List.of(text.split(" ")));

        CommandResult createResult = create.run(new ParamsContainer(createArgs));
        assertFalse(createResult.error(), createResult.message());
        filesToCleanUp.addAll(BackupTestFiles.printSheetPaths(name));

        List<Integer> indices = new ArrayList<>(List.of(1, 2, 3, 4));
        Collections.shuffle(indices, random);

        List<String> restoreArgs = new ArrayList<>(List.of("text", "shamir"));
        for (int i = 1; i <= n; i++)
            restoreArgs.add(indices.subList(0, k).contains(i) ? name + "_" + i + ".png" : "null");

        CommandResult restoreResult = restore.run(new ParamsContainer(restoreArgs));
        assertFalse(restoreResult.error(), restoreResult.message());
        assertEquals(text, restoreResult.message());
    }

    @Test
    void fullSeedWorkflow() throws Exception {
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);

        String[] words = MnemonicUtils.createMnemonic(entropy);
        String name = "e2e_backup_seed";
        int n = 7, k = 4;

        BackupCreateCommand create = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();
        BackupRestoreCommand restore = main.getHandler().getCommand(BackupRestoreCommand.class).orElseThrow();

        for (int i = 1; i <= n; i++)
            filesToCleanUp.add(Path.of(name + "_" + i + ".png"));

        List<String> createArgs = new ArrayList<>(List.of("seed", name, "null", "split", String.valueOf(n), String.valueOf(k)));
        createArgs.addAll(List.of(words));

        CommandResult createResult = create.run(new ParamsContainer(createArgs));
        assertFalse(createResult.error(), createResult.message());
        filesToCleanUp.addAll(BackupTestFiles.printSheetPaths(name));

        List<String> restoreArgs = new ArrayList<>(List.of("seed", "shamir"));
        for (int i = 1; i <= n; i++)
            restoreArgs.add(i > n - k ? name + "_" + i + ".png" : "null");

        CommandResult restoreResult = restore.run(new ParamsContainer(restoreArgs));
        assertFalse(restoreResult.error(), restoreResult.message());

        for (String word : words)
            assertTrue(restoreResult.message().contains(word));
    }
}
