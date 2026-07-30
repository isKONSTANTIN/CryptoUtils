package su.knst.crypto.core.secret;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Compression applied to a secret before it is split, and undone after it is joined. */
public final class GzipCodec {
    private GzipCodec() {
    }

    public static byte[] compress(byte[] data) throws SecretException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        } catch (IOException e) {
            throw new SecretException("Failed to compress source data: " + e.getMessage(), e);
        }

        return out.toByteArray();
    }

    public static byte[] decompress(byte[] data) throws SecretException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            throw new SecretException("Failed to decompress recovered data: " + e.getMessage(), e);
        }
    }
}
