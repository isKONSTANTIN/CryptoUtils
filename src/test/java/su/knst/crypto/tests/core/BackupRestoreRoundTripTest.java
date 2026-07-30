package su.knst.crypto.tests.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import su.knst.crypto.core.backup.BackupRequest;
import su.knst.crypto.core.backup.BackupResult;
import su.knst.crypto.core.backup.BackupService;
import su.knst.crypto.core.render.QrCodec;
import su.knst.crypto.core.restore.RestoreMode;
import su.knst.crypto.core.restore.RestoreRequest;
import su.knst.crypto.core.restore.RestoreService;
import su.knst.crypto.core.restore.ShareInput;
import su.knst.crypto.core.secret.SecretSink;
import su.knst.crypto.core.secret.SecretSource;
import su.knst.crypto.core.seed.SeedService;
import su.knst.crypto.core.shamir.SecretSplitter;
import su.knst.crypto.core.shamir.SplitScheme;
import su.knst.crypto.utils.HexUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Backup and restore against each other, over all three ways a backup can be shaped: split into
 * Shamir shares, printed whole on one card, and reprinted for a share that already exists.
 */
class BackupRestoreRoundTripTest {

    static final SecureRandom RANDOM = new SecureRandom();

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

    void trackArtifacts(BackupResult result) {
        filesToCleanUp.addAll(result.cardFiles());
        filesToCleanUp.addAll(result.tagFiles());
        filesToCleanUp.addAll(result.hexFiles());
        filesToCleanUp.addAll(result.sheetFiles());
    }

    BackupResult backup(String name, SecretSplitter splitter, SecretSource source) throws Exception {
        BackupResult result = new BackupService().create(
                new BackupRequest(name, null, splitter, source, Path.of(".").toAbsolutePath().normalize()));

        trackArtifacts(result);

        return result;
    }

    @Test
    void aShamirSplitFileComesBackFromAnyThresholdSubset() throws Exception {
        byte[] content = new byte[96];
        RANDOM.nextBytes(content);

        Path source = Path.of("roundtrip_source_file");
        filesToCleanUp.add(source);
        Files.write(source, content);

        BackupResult result = backup("roundtrip_file",
                SecretSplitter.shamir(SplitScheme.of(5, 3)), SecretSource.ofFile(source));

        assertEquals(5, result.cardFiles().size());

        Path output = Path.of("roundtrip_file_restored");
        filesToCleanUp.add(output);

        // shares 1, 3 and 5 survive; 2 and 4 are gone, and their slots still hold their place
        List<ShareInput> chunks = List.of(
                new ShareInput.FromFile(result.cardFiles().get(0)),
                new ShareInput.Skipped(),
                new ShareInput.FromFile(result.cardFiles().get(2)),
                new ShareInput.Skipped(),
                new ShareInput.FromFile(result.cardFiles().get(4)));

        new RestoreService().restore(new RestoreRequest(SecretSink.toFile(output), RestoreMode.SHAMIR, chunks));

        assertArrayEquals(content, Files.readAllBytes(output));
    }

    @Test
    void anUnsplitTextBackupComesBackFromItsOneCard() throws Exception {
        String text = "one card holds all of it";

        BackupResult result = backup("roundtrip_single", SecretSplitter.single(), SecretSource.ofText(text));

        assertEquals(1, result.cardFiles().size());
        assertFalse(result.scheme().isSplit());
        // a lone card is already its own sheet
        assertTrue(result.sheetFiles().isEmpty());

        SecretSink.Written written = new RestoreService().restore(new RestoreRequest(
                SecretSink.toText(), RestoreMode.WHOLE,
                List.of(new ShareInput.FromFile(result.cardFiles().get(0)))));

        assertEquals(text, written.text());
    }

    @Test
    void anUnsplitSeedBackupComesBackAsTheSamePhrase() throws Exception {
        String[] mnemonic = SeedService.fromEntropy(SeedService.randomEntropy(32));

        BackupResult result = backup("roundtrip_seed", SecretSplitter.single(), SecretSource.ofSeed(mnemonic));

        SecretSink.Written written = new RestoreService().restore(new RestoreRequest(
                SecretSink.toSeed(), RestoreMode.WHOLE,
                List.of(new ShareInput.FromFile(result.cardFiles().get(0)))));

        assertArrayEquals(mnemonic, written.words());
    }

    @Test
    void aReprintedCardCarriesTheOriginalShareByteForByte() throws Exception {
        byte[] content = "reprint me".getBytes(StandardCharsets.UTF_8);

        BackupResult original = backup("roundtrip_original",
                SecretSplitter.shamir(SplitScheme.of(3, 2)), SecretSource.ofText(new String(content)));

        // share 2's card is lost; its hex was written down, so print a replacement from that
        String knownHex = QrCodec.decode(original.cardFiles().get(1));
        assertNotNull(knownHex);

        BackupResult reprint = backup("roundtrip_reprint",
                SecretSplitter.reprint(2, SplitScheme.of(3, 2)), SecretSource.ofHex(knownHex));

        assertEquals(1, reprint.cardFiles().size());
        assertEquals(knownHex, QrCodec.decode(reprint.cardFiles().get(0)),
                "a reprint must not re-encode the payload");

        // the replacement combines with the sibling that was never lost
        SecretSink.Written written = new RestoreService().restore(new RestoreRequest(
                SecretSink.toText(), RestoreMode.SHAMIR,
                List.of(new ShareInput.FromFile(original.cardFiles().get(0)),
                        new ShareInput.FromFile(reprint.cardFiles().get(0)))));

        assertEquals(new String(content), written.text());
    }

    @Test
    void sharesCanBeTypedBackInAsHex() throws Exception {
        String text = "typed in by hand";

        BackupResult result = backup("roundtrip_hex", SecretSplitter.shamir(SplitScheme.of(3, 2)),
                SecretSource.ofText(text));

        String first = QrCodec.decode(result.cardFiles().get(0));
        String second = QrCodec.decode(result.cardFiles().get(1));

        assertTrue(HexUtils.isValidHex(first));

        SecretSink.Written written = new RestoreService().restore(new RestoreRequest(
                SecretSink.toText(), RestoreMode.SHAMIR,
                List.of(new ShareInput.FromHex(first), new ShareInput.FromHex(second))));

        assertEquals(text, written.text());
    }

    @Test
    void tagsAreWrittenOnlyWhenNamed() throws Exception {
        BackupResult withTags = new BackupService().create(new BackupRequest(
                "roundtrip_tags", "Safe, shelf 2", SecretSplitter.shamir(SplitScheme.of(3, 2)),
                SecretSource.ofText("tagged"), Path.of(".").toAbsolutePath().normalize()));
        trackArtifacts(withTags);

        assertEquals(3, withTags.tagFiles().size());

        BackupResult withoutTags = backup("roundtrip_no_tags",
                SecretSplitter.shamir(SplitScheme.of(3, 2)), SecretSource.ofText("untagged"));

        assertTrue(withoutTags.tagFiles().isEmpty());
    }

    @Test
    void anEmptySourceIsRejected() {
        assertThrows(Exception.class, () -> backup("roundtrip_empty",
                SecretSplitter.single(), SecretSource.ofText("")));
    }

    @Test
    void restoringAWholeBackupFromSeveralCardsIsRejected() throws Exception {
        BackupResult result = backup("roundtrip_wrong_mode",
                SecretSplitter.shamir(SplitScheme.of(3, 2)), SecretSource.ofText("split, not whole"));

        assertThrows(Exception.class, () -> new RestoreService().restore(new RestoreRequest(
                SecretSink.toText(), RestoreMode.WHOLE,
                List.of(new ShareInput.FromFile(result.cardFiles().get(0)),
                        new ShareInput.FromFile(result.cardFiles().get(1))))));
    }
}
