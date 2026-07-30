package su.knst.crypto.tests.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandHandler;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.core.render.QrCodec;
import su.knst.crypto.core.seed.SeedService;
import su.knst.crypto.tests.util.BackupTestFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The backup and restore dialogs driven the way a user goes through them: answer by answer, with a
 * scripted stand-in for the terminal. This is what the old positional-argument tests became once
 * the scripted argument mode moved out of production code.
 */
class BackupDialogE2ETest {

    static final SecureRandom RANDOM = new SecureRandom();

    Main main;
    CommandHandler handler;
    List<Path> filesToCleanUp;

    @BeforeEach
    void setUp() {
        main = new Main(); // don't start because user terminal not needed
        handler = main.getHandler();
        filesToCleanUp = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Path path : filesToCleanUp)
            Files.deleteIfExists(path);
    }

    /** Runs one command through a scripted dialog, as if the answers had been typed at the prompts. */
    CommandResult dialog(String command, String... answers) {
        handler.setQuestioner(new ScriptedQuestioner(answers));

        return handler.run(command);
    }

    void trackBackup(String name, int shares) {
        for (int i = 1; i <= shares; i++) {
            filesToCleanUp.add(Path.of(name + "_" + i + ".png"));
            filesToCleanUp.add(Path.of(name + "_" + i + ".hex"));
        }

        filesToCleanUp.addAll(BackupTestFiles.printSheetPaths(name));
        filesToCleanUp.addAll(BackupTestFiles.tagPaths(name));
    }

    @Test
    void aSplitFileBackupIsCreatedAndRestored() throws Exception {
        byte[] content = new byte[128];
        RANDOM.nextBytes(content);

        Path source = Path.of("dialog_source_file");
        Path restored = Path.of("dialog_restored_file");
        filesToCleanUp.add(source);
        filesToCleanUp.add(restored);
        Files.write(source, content);

        String name = "dialog_file";

        CommandResult created = dialog("backup",
                "file",              // source type
                name,                // backup name
                "",                  // no tags
                "split",             // split mode
                "5", "3",            // N, K
                source.toString());  // path

        assertFalse(created.error(), created.message());
        trackBackup(name, 5);

        for (int i = 1; i <= 5; i++)
            assertTrue(Path.of(name + "_" + i + ".png").toFile().isFile());

        CommandResult recovered = dialog("restore",
                "file",                 // output type
                restored.toString(),    // output path
                "shamir",               // what is on the cards
                "5",                    // how many parts there were
                name + "_1.png",        // share 1
                "",                     // share 2 is gone
                name + "_3.png",        // share 3
                "",                     // share 4 is gone
                name + "_5.png");       // share 5

        assertFalse(recovered.error(), recovered.message());
        assertArrayEquals(content, Files.readAllBytes(restored));
    }

    @Test
    void aSplitTextBackupIsCreatedAndRestored() {
        String text = "correct horse battery staple";
        String name = "dialog_text";

        CommandResult created = dialog("backup", "text", name, "", "split", "3", "2", text);

        assertFalse(created.error(), created.message());
        trackBackup(name, 3);

        CommandResult recovered = dialog("restore",
                "text", "shamir", "3", name + "_1.png", name + "_2.png", "");

        assertFalse(recovered.error(), recovered.message());
        assertTrue(recovered.message().contains(text), recovered.message());
    }

    @Test
    void aSplitSeedBackupIsCreatedAndRestored() throws Exception {
        String[] mnemonic = SeedService.fromEntropy(SeedService.randomEntropy(32));
        String name = "dialog_seed";

        CommandResult created = dialog("backup",
                "seed", name, "", "split", "3", "2", String.join(" ", mnemonic));

        assertFalse(created.error(), created.message());
        trackBackup(name, 3);

        CommandResult recovered = dialog("restore",
                "seed", "shamir", "3", "", name + "_2.png", name + "_3.png");

        assertFalse(recovered.error(), recovered.message());

        for (String word : mnemonic)
            assertTrue(recovered.message().contains(word), "missing '" + word + "' in recovered phrase");
    }

    @Test
    void anUnsplitBackupIsCreatedAndRestoredFromItsOneCard() {
        String text = "all of it on one card";
        String name = "dialog_single";

        CommandResult created = dialog("backup", "text", name, "", "single", text);

        assertFalse(created.error(), created.message());
        trackBackup(name, 1);

        assertTrue(created.message().contains("single card"), created.message());

        CommandResult recovered = dialog("restore", "text", "whole", name + "_1.png");

        assertFalse(recovered.error(), recovered.message());
        assertTrue(recovered.message().contains(text), recovered.message());
    }

    @Test
    void aLostCardIsReprintedFromItsHexAndStillCombines() throws Exception {
        String text = "reprint this";
        String name = "dialog_original";
        String reprintName = "dialog_reprint";

        CommandResult created = dialog("backup", "text", name, "", "split", "3", "2", text);
        assertFalse(created.error(), created.message());
        trackBackup(name, 3);

        String knownHex = QrCodec.decode(Path.of(name + "_2.png"));
        assertNotNull(knownHex);

        CommandResult reprinted = dialog("backup",
                "hex",          // payload type
                reprintName,    // card name
                "",             // no tags
                "2",            // which share this card is for
                "3", "2",       // N, K
                knownHex);

        assertFalse(reprinted.error(), reprinted.message());
        trackBackup(reprintName, 3);

        assertEquals(knownHex, QrCodec.decode(Path.of(reprintName + "_2.png")),
                "a reprint must carry the original payload byte for byte");

        CommandResult recovered = dialog("restore",
                "text", "shamir", "3", name + "_1.png", reprintName + "_2.png", "");

        assertFalse(recovered.error(), recovered.message());
        assertTrue(recovered.message().contains(text), recovered.message());
    }

    @Test
    void tagsArePrintedWhenTheDialogNamesThem() {
        String name = "dialog_tags";

        CommandResult created = dialog("backup", "text", name, "Safe, shelf 2", "split", "3", "2", "tagged");

        assertFalse(created.error(), created.message());
        trackBackup(name, 3);

        for (int i = 1; i <= 3; i++)
            assertTrue(Path.of(name + "_tag_" + i + ".png").toFile().isFile());
    }

    @Test
    void cancellingAtTheFirstPromptWritesNothing() {
        CommandResult result = dialog("backup");

        assertTrue(result.error());
        assertFalse(Path.of("_1.png").toFile().exists());
    }

    @Test
    void anInvalidSchemeIsReportedWithoutWritingCards() {
        CommandResult result = dialog("backup", "text", "dialog_bad", "", "split", "2", "5", "nope");

        assertTrue(result.error());
        assertFalse(Path.of("dialog_bad_1.png").toFile().exists());
    }

    @Test
    void restoringWithNoUsableSharesIsAnError() {
        CommandResult result = dialog("restore", "text", "shamir", "3", "", "", "");

        assertTrue(result.error());
    }

    @Test
    void typedArgumentsAreReportedAsIgnoredRatherThanDropped() {
        CommandResult result = dialog("backup file secret.txt");

        // the dialog was cancelled (no answers scripted), but the point is the notice
        assertTrue(result.message().contains("ignored: file secret.txt"), result.message());
    }

    @Test
    void helpTakesItsArgumentOnTheLine() {
        CommandResult result = dialog("help backup");

        assertFalse(result.error());
        assertTrue(result.message().contains("backup"));
        assertFalse(result.message().contains("ignored:"));
    }

    @Test
    void unicodeTextSurvivesTheRoundTrip() {
        String text = "секрет: пароль от сейфа";
        String name = "dialog_unicode";

        CommandResult created = dialog("backup", "text", name, "", "split", "3", "2", text);
        assertFalse(created.error(), created.message());
        trackBackup(name, 3);

        CommandResult recovered = dialog("restore",
                "text", "shamir", "3", name + "_1.png", "", name + "_3.png");

        assertFalse(recovered.error(), recovered.message());
        assertTrue(recovered.message().contains(text), recovered.message());
        assertEquals(text, new String(recovered.message().getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8).trim());
    }
}
