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
import su.knst.crypto.utils.codes.SimpleQRCodeWorker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackupRestoreCommandTest {

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

    private void createBackup(String type, String name, int n, int k, List<String> source) {
        BackupCreateCommand createCommand = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();

        for (int i = 1; i <= n; i++)
            filesToCleanUp.add(Path.of(name + "_" + i + ".png"));

        List<String> args = new ArrayList<>(List.of(type, name, "null", String.valueOf(n), String.valueOf(k)));
        args.addAll(source);

        CommandResult result = createCommand.run(new ParamsContainer(args));

        assertFalse(result.error(), "backup setup failed: " + result.message());

        filesToCleanUp.addAll(BackupTestFiles.printSheetPaths(name));
    }

    // builds ["restore", type, (outputPath), chunk_1, chunk_2, ...] using only `chosen` indices
    // (1-based), each provided either as the PNG file path or, for `hexSlots`, as the raw hex
    // string read out of that PNG - mixing both entry methods in one reconstruction
    private List<String> chunkArgs(String name, int n, List<Integer> chosen, List<Integer> hexSlots) throws Exception {
        List<String> tokens = new ArrayList<>();

        for (int i = 1; i <= n; i++) {
            if (!chosen.contains(i)) {
                tokens.add("null");
                continue;
            }

            String pngPath = name + "_" + i + ".png";

            if (hexSlots.contains(i))
                tokens.add(new SimpleQRCodeWorker().readCode(pngPath));
            else
                tokens.add(pngPath);
        }

        return tokens;
    }

    @Test
    void restoresAFileFromMixedFileAndHexChunks() throws Exception {
        byte[] content = new byte[64];
        random.nextBytes(content);

        Path source = Path.of("backup_restore_test_source_file");
        filesToCleanUp.add(source);
        Files.write(source, content);

        String name = "backup_restore_test_file";
        int n = 5, k = 3;

        createBackup("file", name, n, k, List.of(source.toString()));

        Path output = Path.of("backup_restore_test_output_file");
        filesToCleanUp.add(output);

        List<String> tokens = chunkArgs(name, n, List.of(1, 3, 5), List.of(3));

        BackupRestoreCommand restoreCommand = main.getHandler().getCommand(BackupRestoreCommand.class).orElseThrow();

        List<String> args = new ArrayList<>(List.of("file", output.toString()));
        args.addAll(tokens);

        CommandResult result = restoreCommand.run(new ParamsContainer(args));

        assertFalse(result.error(), result.message());
        assertArrayEquals(content, Files.readAllBytes(output));
    }

    @Test
    void restoresTextFromDifferentSubsets() throws Exception {
        String text = "a short secret note to back up";
        String name = "backup_restore_test_text";
        int n = 5, k = 3;

        createBackup("text", name, n, k, List.of(text.split(" ")));

        BackupRestoreCommand restoreCommand = main.getHandler().getCommand(BackupRestoreCommand.class).orElseThrow();

        for (List<Integer> chosen : List.of(List.of(1, 2, 3), List.of(2, 4, 5), List.of(1, 3, 5))) {
            List<String> tokens = chunkArgs(name, n, chosen, List.of(chosen.get(0)));

            List<String> args = new ArrayList<>(List.of("text"));
            args.addAll(tokens);

            CommandResult result = restoreCommand.run(new ParamsContainer(args));

            assertFalse(result.error(), "failed for subset " + chosen + ": " + result.message());
            assertEquals(text, result.message());
        }
    }

    @Test
    void restoresSeedFromMixedFileAndHexChunks() throws Exception {
        byte[] entropy = new byte[16];
        random.nextBytes(entropy);

        String[] words = MnemonicUtils.createMnemonic(entropy);
        String name = "backup_restore_test_seed";
        int n = 5, k = 3;

        createBackup("seed", name, n, k, List.of(words));

        List<String> tokens = chunkArgs(name, n, List.of(2, 3, 4), List.of(4));

        BackupRestoreCommand restoreCommand = main.getHandler().getCommand(BackupRestoreCommand.class).orElseThrow();

        List<String> args = new ArrayList<>(List.of("seed"));
        args.addAll(tokens);

        CommandResult result = restoreCommand.run(new ParamsContainer(args));

        assertFalse(result.error(), result.message());

        for (String word : words)
            assertTrue(result.message().contains(word));
    }

    @Test
    void invalidHexStringIsError() {
        BackupRestoreCommand restoreCommand = main.getHandler().getCommand(BackupRestoreCommand.class).orElseThrow();

        CommandResult result = restoreCommand.run(new ParamsContainer("text", "not-valid-hex", "null", "null"));

        assertTrue(result.error());
    }

    @Test
    void noChunksProvidedIsError() {
        BackupRestoreCommand restoreCommand = main.getHandler().getCommand(BackupRestoreCommand.class).orElseThrow();

        CommandResult result = restoreCommand.run(new ParamsContainer("text", "null", "null", "null"));

        assertTrue(result.error());
    }

    @Test
    void missingOutputPathForFileTypeIsError() {
        BackupRestoreCommand restoreCommand = main.getHandler().getCommand(BackupRestoreCommand.class).orElseThrow();

        CommandResult result = restoreCommand.run(new ParamsContainer("file"));

        assertTrue(result.error());
    }

    @Test
    void unknownTypeIsError() {
        BackupRestoreCommand restoreCommand = main.getHandler().getCommand(BackupRestoreCommand.class).orElseThrow();

        CommandResult result = restoreCommand.run(new ParamsContainer("unknown", "aabbcc"));

        assertTrue(result.error());
    }
}
