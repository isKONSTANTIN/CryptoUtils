package su.knst.crypto.tests.core.backup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import su.knst.crypto.core.backup.BackupException;
import su.knst.crypto.core.backup.BackupRequest;
import su.knst.crypto.core.backup.BackupResult;
import su.knst.crypto.core.backup.BackupService;
import su.knst.crypto.core.render.QrCodec;
import su.knst.crypto.core.secret.SecretSource;
import su.knst.crypto.core.seed.SeedService;
import su.knst.crypto.core.shamir.SecretSplitter;
import su.knst.crypto.core.shamir.SplitScheme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackupServiceTest {

    static final SecureRandom RANDOM = new SecureRandom();
    static final Path HERE = Path.of(".").toAbsolutePath().normalize();

    List<Path> filesToCleanUp;

    @BeforeEach
    void setUp() {
        filesToCleanUp = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Path path : filesToCleanUp)
            Files.deleteIfExists(path);
    }

    BackupResult backup(BackupRequest request) throws BackupException {
        BackupResult result = new BackupService().create(request);

        filesToCleanUp.addAll(result.cardFiles());
        filesToCleanUp.addAll(result.tagFiles());
        filesToCleanUp.addAll(result.hexFiles());
        filesToCleanUp.addAll(result.sheetFiles());

        return result;
    }

    BackupRequest request(String name, String tagName, SecretSplitter splitter, SecretSource source) {
        return new BackupRequest(name, tagName, splitter, source, HERE);
    }

    @Test
    void writesOneCardPerShare() throws Exception {
        BackupResult result = backup(request("service_cards", null,
                SecretSplitter.shamir(SplitScheme.of(4, 2)), SecretSource.ofText("secret")));

        assertEquals(4, result.cardFiles().size());

        for (int i = 0; i < 4; i++)
            assertTrue(result.cardFiles().get(i).toFile().isFile(),
                    result.cardFiles().get(i) + " should exist");
    }

    @Test
    void everyArtifactIsWrittenOwnerOnly() throws Exception {
        BackupResult result = backup(request("service_perms", "Shelf 3",
                SecretSplitter.shamir(SplitScheme.of(3, 2)), SecretSource.ofText("secret")));

        List<Path> all = new ArrayList<>(result.cardFiles());
        all.addAll(result.tagFiles());
        all.addAll(result.sheetFiles());

        assertFalse(all.isEmpty());

        for (Path path : all)
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(path)),
                    path + " must be owner-only");
    }

    @Test
    void anEmptySourceIsRejectedBeforeAnythingIsWritten() {
        BackupException error = assertThrows(BackupException.class, () -> backup(request("service_empty", null,
                SecretSplitter.single(), SecretSource.ofText(""))));

        assertTrue(error.getMessage().contains("empty"));
        assertFalse(Path.of("service_empty_1.png").toFile().exists());
    }

    @Test
    void anInvalidMnemonicIsRejectedBeforeAnythingIsWritten() {
        String[] words = new String[12];
        java.util.Arrays.fill(words, "abandon");
        words[11] = "definitelynotabip39word";

        assertThrows(BackupException.class, () -> backup(request("service_bad_seed", null,
                SecretSplitter.shamir(SplitScheme.of(3, 2)), SecretSource.ofSeed(words))));

        assertFalse(Path.of("service_bad_seed_1.png").toFile().exists());
    }

    @Test
    void aSeedBackupStoresItsEntropy() throws Exception {
        byte[] entropy = SeedService.randomEntropy(32);
        String[] mnemonic = SeedService.fromEntropy(entropy);

        BackupResult result = backup(request("service_seed", null,
                SecretSplitter.single(), SecretSource.ofSeed(mnemonic)));

        assertEquals(1, result.cardFiles().size());
        assertNotNull(QrCodec.decode(result.cardFiles().get(0)));
    }

    @Test
    void aSecretTooLargeForAQrStillProducesCardsAndAHexFile() throws Exception {
        byte[] content = new byte[8192];
        RANDOM.nextBytes(content);

        Path source = Path.of("service_large_source");
        filesToCleanUp.add(source);
        Files.write(source, content);

        BackupResult result = backup(request("service_large", null,
                SecretSplitter.shamir(SplitScheme.of(2, 2)), SecretSource.ofFile(source)));

        assertFalse(result.hasQr());
        assertEquals(2, result.cardFiles().size());

        for (Path card : result.cardFiles())
            assertNull(QrCodec.decode(card), "no QR should be present on " + card);

        // retyping a multi-kilobyte hex block by hand is not a recovery plan, so the hex ships too
        assertEquals(2, result.hexFiles().size());

        for (Path hex : result.hexFiles()) {
            assertTrue(hex.toFile().isFile());
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(hex)));
        }
    }

    @Test
    void tagsAreWrittenOnePerShareWhenNamed() throws Exception {
        BackupResult result = backup(request("service_tags", "Safe, shelf 2",
                SecretSplitter.shamir(SplitScheme.of(3, 2)), SecretSource.ofText("tagged")));

        assertEquals(3, result.tagFiles().size());

        for (Path tag : result.tagFiles())
            assertTrue(tag.toFile().isFile());
    }

    @Test
    void noTagNameMeansNoTags() throws Exception {
        BackupResult result = backup(request("service_no_tags", null,
                SecretSplitter.shamir(SplitScheme.of(3, 2)), SecretSource.ofText("untagged")));

        assertTrue(result.tagFiles().isEmpty());
    }

    @Test
    void anUnwritableDirectoryIsReported() {
        BackupException error = assertThrows(BackupException.class, () -> new BackupService().create(
                new BackupRequest("service_nowhere", null, SecretSplitter.single(),
                        SecretSource.ofText("nowhere to go"), HERE.resolve("no_such_directory_here"))));

        assertTrue(error.getMessage().contains("Failed to write backup files"), error.getMessage());
    }

    @Test
    void printSheetsAreProducedForMultipleArtifacts() throws Exception {
        BackupResult result = backup(request("service_sheets", null,
                SecretSplitter.shamir(SplitScheme.of(3, 2)), SecretSource.ofText("sheet me")));

        assertFalse(result.sheetFiles().isEmpty());
        assertTrue(result.sheetFailure().isEmpty());
    }
}
