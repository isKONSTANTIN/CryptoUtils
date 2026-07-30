package su.knst.crypto.tests.core.backup;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.junit.jupiter.api.Test;
import su.knst.crypto.core.backup.BackupException;
import su.knst.crypto.core.backup.CardBuilder;
import su.knst.crypto.core.render.QrCodec;
import su.knst.crypto.core.secret.SecretType;
import su.knst.crypto.core.shamir.SecretSplitter;
import su.knst.crypto.core.shamir.Share;
import su.knst.crypto.core.shamir.ShareSet;
import su.knst.crypto.core.shamir.SplitScheme;

import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CardBuilderTest {

    static final SecureRandom RANDOM = new SecureRandom();

    static CardBuilder.CardMeta meta() {
        return new CardBuilder.CardMeta("test_backup", SecretType.FILE, LocalDate.of(2026, 7, 30));
    }

    static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);

        return bytes;
    }

    @Test
    void everyCardCarriesAQrThatDecodesBackToItsOwnShare() throws BackupException {
        CardBuilder.CardSet cards = CardBuilder.build(
                SecretSplitter.shamir(SplitScheme.of(3, 2)), randomBytes(48), meta());

        assertTrue(cards.hasQr());
        assertEquals(3, cards.images().size());

        List<Share> shares = cards.shares().shares();

        for (int i = 0; i < shares.size(); i++)
            assertEquals(shares.get(i).hex(), QrCodec.decode(cards.images().get(i)),
                    "card " + (i + 1) + " must decode back to its own share");
    }

    @Test
    void oneErrorCorrectionLevelAppliesToTheWholeSet() throws BackupException {
        CardBuilder.CardSet cards = CardBuilder.build(
                SecretSplitter.shamir(SplitScheme.of(4, 2)), randomBytes(64), meta());

        assertNotNull(cards.appliedLevel());
        assertTrue(List.of(QrCodec.LEVELS).contains(cards.appliedLevel()));
    }

    @Test
    void smallPayloadsGetTheStrongestErrorCorrection() throws BackupException {
        CardBuilder.CardSet cards = CardBuilder.build(
                SecretSplitter.single(), randomBytes(16), meta());

        assertEquals(ErrorCorrectionLevel.H, cards.appliedLevel());
        assertTrue(cards.hasQr());
    }

    @Test
    void anOversizedPayloadFallsBackToAQrLessCard() throws BackupException {
        // far past what any QR version can hold at any error-correction level
        CardBuilder.CardSet cards = CardBuilder.build(
                SecretSplitter.single(), randomBytes(8192), meta());

        assertFalse(cards.hasQr());
        assertEquals(1, cards.images().size());
        assertEquals(QrCodec.LEVELS[QrCodec.LEVELS.length - 1], cards.appliedLevel());
    }

    @Test
    void aSingleCardHoldsTheWholeSecret() throws BackupException {
        byte[] secret = randomBytes(32);

        CardBuilder.CardSet cards = CardBuilder.build(SecretSplitter.single(), secret, meta());

        assertEquals(1, cards.shares().size());
        assertArrayEquals(secret, cards.shares().get(1).data());
    }

    @Test
    void aReprintKeepsTheOriginalShareNumbering() throws BackupException {
        CardBuilder.CardSet cards = CardBuilder.build(
                SecretSplitter.reprint(4, SplitScheme.of(6, 3)), randomBytes(32), meta());

        assertEquals(4, cards.shares().get(4).index());
        assertEquals(6, cards.shares().scheme().total());
        assertEquals(3, cards.shares().scheme().threshold());
    }

    @Test
    void aNonRandomizedSplitterIsNeverRetried() throws BackupException {
        AtomicInteger calls = new AtomicInteger();

        SecretSplitter counting = new SecretSplitter() {
            @Override
            public ShareSet split(byte[] secret) {
                calls.incrementAndGet();

                // oversized on purpose: no error-correction level will fit, so a randomized
                // splitter would be asked to re-split up to the retry limit
                return new ShareSet(SplitScheme.single(), List.of(new Share(1, randomBytes(8192))));
            }

            @Override
            public SplitScheme scheme() {
                return SplitScheme.single();
            }

            @Override
            public boolean randomized() {
                return false;
            }

            @Override
            public boolean compress() {
                return true;
            }
        };

        CardBuilder.CardSet cards = CardBuilder.build(counting, randomBytes(16), meta());

        assertEquals(1, calls.get());
        assertFalse(cards.hasQr());
    }

    @Test
    void cardImagesAreTallerThanTheyAreWide() throws BackupException {
        CardBuilder.CardSet cards = CardBuilder.build(SecretSplitter.single(), randomBytes(32), meta());
        BufferedImage card = cards.images().get(0);

        assertTrue(card.getHeight() > card.getWidth());
    }
}
