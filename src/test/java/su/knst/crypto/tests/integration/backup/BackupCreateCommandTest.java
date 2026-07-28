package su.knst.crypto.tests.integration.backup;

import com.codahale.shamir.Scheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.backup.BackupCreateCommand;
import su.knst.crypto.tests.util.BackupTestFiles;
import su.knst.crypto.utils.HexUtils;
import su.knst.crypto.utils.MnemonicUtils;
import su.knst.crypto.utils.codes.SimpleQRCodeWorker;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class BackupCreateCommandTest {

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

    private byte[] decodeChunk(String name, int index) throws Exception {
        Path path = Path.of(name + "_" + index + ".png");
        String hex = new SimpleQRCodeWorker().readCode(path.toString());
        return HexUtils.hexStringToByteArray(hex);
    }

    private byte[] reconstruct(String name, int n, int k) throws Exception {
        Map<Integer, byte[]> parts = new HashMap<>();

        for (int i = 1; i <= k; i++)
            parts.put(i, decodeChunk(name, i));

        byte[] joined = new Scheme(new SecureRandom(), n, k).join(parts);

        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(joined))) {
            return gzip.readAllBytes();
        }
    }

    @Test
    void backsUpAFile() throws Exception {
        byte[] content = new byte[64];
        random.nextBytes(content);

        Path source = Path.of("backup_create_test_source_file");
        filesToCleanUp.add(source);
        Files.write(source, content);

        String name = "backup_create_test_file";
        int n = 5, k = 3;

        for (int i = 1; i <= n; i++)
            filesToCleanUp.add(Path.of(name + "_" + i + ".png"));

        BackupCreateCommand command = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();
        CommandResult result = command.run(new ParamsContainer("file", name, String.valueOf(n), String.valueOf(k), source.toString()));

        assertFalse(result.error(), result.message());
        filesToCleanUp.addAll(BackupTestFiles.printSheetPaths(name));

        for (int i = 1; i <= n; i++)
            assertTrue(Path.of(name + "_" + i + ".png").toFile().isFile());

        byte[] recovered = reconstruct(name, n, k);

        assertArrayEquals(content, recovered);
    }

    @Test
    void backsUpText() throws Exception {
        String text = "hello world, this is a backed up secret note";
        String name = "backup_create_test_text";
        int n = 4, k = 2;

        for (int i = 1; i <= n; i++)
            filesToCleanUp.add(Path.of(name + "_" + i + ".png"));

        BackupCreateCommand command = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();

        List<String> args = new ArrayList<>(List.of("text", name, String.valueOf(n), String.valueOf(k)));
        args.addAll(List.of(text.split(" ")));

        CommandResult result = command.run(new ParamsContainer(args));

        assertFalse(result.error(), result.message());
        filesToCleanUp.addAll(BackupTestFiles.printSheetPaths(name));

        byte[] recovered = reconstruct(name, n, k);

        String decompressed = new String(recovered, StandardCharsets.UTF_8);

        assertEquals(text, decompressed);
    }

    @Test
    void backsUpSeed() throws Exception {
        byte[] entropy = new byte[16];
        random.nextBytes(entropy);

        String[] words = MnemonicUtils.createMnemonic(entropy);
        String name = "backup_create_test_seed";
        int n = 5, k = 3;

        for (int i = 1; i <= n; i++)
            filesToCleanUp.add(Path.of(name + "_" + i + ".png"));

        BackupCreateCommand command = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();

        List<String> args = new ArrayList<>(List.of("seed", name, String.valueOf(n), String.valueOf(k)));
        args.addAll(List.of(words));

        CommandResult result = command.run(new ParamsContainer(args));

        assertFalse(result.error(), result.message());
        filesToCleanUp.addAll(BackupTestFiles.printSheetPaths(name));

        byte[] recovered = reconstruct(name, n, k);

        assertArrayEquals(entropy, recovered);
    }

    @Test
    void tooFewPartsForRecoverIsError() {
        BackupCreateCommand command = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("text", "bad_k", "5", "1", "hello"));

        assertTrue(result.error());
        assertFalse(Path.of("bad_k_1.png").toFile().exists());
    }

    @Test
    void allPartsLessThanRequiredIsError() {
        BackupCreateCommand command = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("text", "bad_n", "2", "3", "hello"));

        assertTrue(result.error());
        assertFalse(Path.of("bad_n_1.png").toFile().exists());
    }

    @Test
    void wordNotInDictionaryIsError() {
        BackupCreateCommand command = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();

        List<String> args = new ArrayList<>(List.of("seed", "bad_seed", "5", "3"));
        for (int i = 0; i < 11; i++)
            args.add("abandon");
        args.add("notarealbip39word");

        CommandResult result = command.run(new ParamsContainer(args));

        assertTrue(result.error());
        assertFalse(Path.of("bad_seed_1.png").toFile().exists());
    }

    @Test
    void secretTooLargeForQrFallsBackToCardsWithoutQr() throws Exception {
        byte[] content = new byte[10_000];
        random.nextBytes(content);

        Path source = Path.of("backup_create_test_huge_source_file");
        filesToCleanUp.add(source);
        Files.write(source, content);

        String name = "backup_create_test_huge";
        int n = 3, k = 2;

        for (int i = 1; i <= n; i++)
            filesToCleanUp.add(Path.of(name + "_" + i + ".png"));

        BackupCreateCommand command = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();
        CommandResult result = command.run(new ParamsContainer("file", name, String.valueOf(n), String.valueOf(k), source.toString()));

        assertFalse(result.error(), result.message());
        filesToCleanUp.addAll(BackupTestFiles.printSheetPaths(name));

        for (int i = 1; i <= n; i++) {
            Path path = Path.of(name + "_" + i + ".png");

            assertTrue(path.toFile().isFile());
            assertNull(new SimpleQRCodeWorker().readCode(path.toString()));
        }
    }

    @Test
    void missingSourceIsError() {
        BackupCreateCommand command = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("file", "no_source", "3", "2", "this_file_does_not_exist_hopefully"));

        assertTrue(result.error());
    }

    @Test
    void unknownTypeIsError() {
        BackupCreateCommand command = main.getHandler().getCommand(BackupCreateCommand.class).orElseThrow();

        CommandResult result = command.run(new ParamsContainer("unknown", "name", "3", "2", "hello"));

        assertTrue(result.error());
    }
}
