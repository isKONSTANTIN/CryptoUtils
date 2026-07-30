package su.knst.crypto.core.restore;

import su.knst.crypto.core.render.QrCodec;
import su.knst.crypto.utils.HexUtils;

/** Turns one restore input into the share bytes it stands for. */
final class ShareReader {
    private ShareReader() {
    }

    static byte[] read(ShareInput input, int index) throws RestoreException {
        String hex;

        if (input instanceof ShareInput.FromFile file)
            hex = readCard(file, index);
        else if (input instanceof ShareInput.FromHex raw)
            hex = raw.hex().trim();
        else
            throw new IllegalArgumentException("Skipped inputs must not reach the reader");

        if (!HexUtils.isValidHex(hex))
            throw new RestoreException("Chunk " + index + ": invalid hex string");

        return HexUtils.hexStringToByteArray(hex);
    }

    private static String readCard(ShareInput.FromFile file, int index) throws RestoreException {
        String hex;

        try {
            hex = QrCodec.decode(file.path());
        } catch (Exception e) {
            throw new RestoreException("Chunk " + index + ": failed to read QR code: " + e.getMessage(), e);
        }

        if (hex == null)
            throw new RestoreException("Chunk " + index + ": QR code not found in image");

        return hex;
    }
}
