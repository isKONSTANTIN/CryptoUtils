package su.knst.crypto.tests.core.restore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import su.knst.crypto.core.render.CardImage;
import su.knst.crypto.core.render.PngWriter;
import su.knst.crypto.core.restore.RestoreException;
import su.knst.crypto.core.restore.RestoreMode;
import su.knst.crypto.core.restore.RestoreRequest;
import su.knst.crypto.core.restore.RestoreService;
import su.knst.crypto.core.restore.ShareInput;
import su.knst.crypto.core.secret.GzipCodec;
import su.knst.crypto.core.secret.SecretSink;
import su.knst.crypto.core.shamir.SecretSplitter;
import su.knst.crypto.core.shamir.Share;
import su.knst.crypto.core.shamir.ShareSet;
import su.knst.crypto.core.shamir.SplitScheme;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RestoreServiceTest {

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

    static ShareSet split(String text) throws Exception {
        return SecretSplitter.shamir(SplitScheme.of(3, 2))
                .split(GzipCodec.compress(text.getBytes(StandardCharsets.UTF_8)));
    }

    static List<ShareInput> hexOf(ShareSet shares, int... indices) {
        List<ShareInput> chunks = new ArrayList<>();

        for (int i = 1; i <= shares.scheme().total(); i++) {
            boolean wanted = false;

            for (int index : indices)
                wanted |= index == i;

            chunks.add(wanted ? new ShareInput.FromHex(shares.get(i).hex()) : new ShareInput.Skipped());
        }

        return chunks;
    }

    @Test
    void hexSharesRestoreTheSecret() throws Exception {
        String text = "restore me";
        ShareSet shares = split(text);

        SecretSink.Written written = new RestoreService().restore(
                new RestoreRequest(SecretSink.toText(), RestoreMode.SHAMIR, hexOf(shares, 1, 3)));

        assertEquals(text, written.text());
    }

    @Test
    void hexIsAcceptedInEitherCaseAndWithSurroundingSpace() throws Exception {
        String text = "case insensitive";
        ShareSet shares = split(text);

        List<ShareInput> chunks = List.of(
                new ShareInput.FromHex("  " + shares.get(1).hex().toLowerCase() + "  "),
                new ShareInput.FromHex(shares.get(2).hex()),
                new ShareInput.Skipped());

        assertEquals(text, new RestoreService()
                .restore(new RestoreRequest(SecretSink.toText(), RestoreMode.SHAMIR, chunks)).text());
    }

    @Test
    void skippedSlotsStillHoldTheirPlace() throws Exception {
        String text = "numbering matters";
        ShareSet shares = split(text);

        // shares 2 and 3, given in their real slots
        List<ShareInput> correct = List.of(
                new ShareInput.Skipped(),
                new ShareInput.FromHex(shares.get(2).hex()),
                new ShareInput.FromHex(shares.get(3).hex()));

        assertEquals(text, new RestoreService()
                .restore(new RestoreRequest(SecretSink.toText(), RestoreMode.SHAMIR, correct)).text());

        // the same two shares shifted up into slots 1 and 2 are a different polynomial
        List<ShareInput> shifted = List.of(
                new ShareInput.FromHex(shares.get(2).hex()),
                new ShareInput.FromHex(shares.get(3).hex()),
                new ShareInput.Skipped());

        assertThrows(RestoreException.class, () -> new RestoreService()
                .restore(new RestoreRequest(SecretSink.toText(), RestoreMode.SHAMIR, shifted)));
    }

    @Test
    void noChunksAtAllIsAnError() {
        RestoreException error = assertThrows(RestoreException.class, () -> new RestoreService()
                .restore(new RestoreRequest(SecretSink.toText(), RestoreMode.SHAMIR, List.of())));

        assertTrue(error.getMessage().contains("No chunks"));
    }

    @Test
    void onlySkippedChunksIsAnError() {
        assertThrows(RestoreException.class, () -> new RestoreService().restore(new RestoreRequest(
                SecretSink.toText(), RestoreMode.SHAMIR,
                List.of(new ShareInput.Skipped(), new ShareInput.Skipped()))));
    }

    @Test
    void aChunkThatIsNotHexIsReportedWithItsNumber() {
        RestoreException error = assertThrows(RestoreException.class, () -> new RestoreService()
                .restore(new RestoreRequest(SecretSink.toText(), RestoreMode.SHAMIR,
                        List.of(new ShareInput.Skipped(), new ShareInput.FromHex("not-hex-at-all")))));

        assertTrue(error.getMessage().contains("Chunk 2"), error.getMessage());
        assertTrue(error.getMessage().contains("invalid hex"));
    }

    @Test
    void anImageWithoutAQrIsReported() throws Exception {
        // a QR-less card: the renderer will happily produce one, but there is nothing to scan
        Path card = Path.of("restore_test_no_qr.png");
        filesToCleanUp.add(card);

        PngWriter.write(CardImage.build(new CardImage.ShareCardData(
                "no_qr", 1, 1, 1, LocalDate.of(2026, 7, 30),
                "00112233", "a1b2c3", "TEXT", ErrorCorrectionLevel.L, false)), card);

        RestoreException error = assertThrows(RestoreException.class, () -> new RestoreService()
                .restore(new RestoreRequest(SecretSink.toText(), RestoreMode.SHAMIR,
                        List.of(new ShareInput.FromFile(card)))));

        assertTrue(error.getMessage().contains("QR code not found"), error.getMessage());
    }

    @Test
    void anUnreadableImageFileIsReported() {
        RestoreException error = assertThrows(RestoreException.class, () -> new RestoreService()
                .restore(new RestoreRequest(SecretSink.toText(), RestoreMode.SHAMIR,
                        List.of(new ShareInput.FromFile(Path.of("restore_test_definitely_missing.png"))))));

        assertTrue(error.getMessage().contains("Chunk 1"), error.getMessage());
    }

    @Test
    void tooFewSharesFailToDecompressRatherThanReturningGarbage() throws Exception {
        ShareSet shares = split("needs two of three");

        RestoreException error = assertThrows(RestoreException.class, () -> new RestoreService()
                .restore(new RestoreRequest(SecretSink.toText(), RestoreMode.SHAMIR, hexOf(shares, 1))));

        assertTrue(error.getMessage().contains("decompress"), error.getMessage());
    }

    @Test
    void sharesOfDifferentLengthsAreReportedRatherThanCrashing() {
        // shares from two different backups: Shamir itself refuses to combine them
        List<ShareInput> mismatched = List.of(
                new ShareInput.FromHex("00112233"),
                new ShareInput.FromHex("00112233445566"));

        RestoreException error = assertThrows(RestoreException.class, () -> new RestoreService()
                .restore(new RestoreRequest(SecretSink.toText(), RestoreMode.SHAMIR, mismatched)));

        assertTrue(error.getMessage().contains("Failed to reconstruct secret"), error.getMessage());
    }

    @Test
    void aWholeBackupNeedsExactlyOneCard() throws Exception {
        ShareSet shares = split("not actually whole");

        RestoreException error = assertThrows(RestoreException.class, () -> new RestoreService()
                .restore(new RestoreRequest(SecretSink.toText(), RestoreMode.WHOLE, hexOf(shares, 1, 2))));

        assertTrue(error.getMessage().contains("single card"), error.getMessage());
    }

    @Test
    void aWholeBackupIsReadStraightOffItsCard() throws Exception {
        String text = "whole and undivided";

        byte[] payload = GzipCodec.compress(text.getBytes(StandardCharsets.UTF_8));
        Share only = new Share(1, payload);

        SecretSink.Written written = new RestoreService().restore(new RestoreRequest(
                SecretSink.toText(), RestoreMode.WHOLE, List.of(new ShareInput.FromHex(only.hex()))));

        assertEquals(text, written.text());
    }
}
