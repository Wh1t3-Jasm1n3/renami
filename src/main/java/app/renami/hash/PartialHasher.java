package app.renami.hash;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Fast partial hashing for duplicate pre-filtering. */
public final class PartialHasher {

    private static final int PARTIAL_BYTES = 1024 * 1024;

    private PartialHasher() {
    }

    /**
     * SHA-256 over (file size + first 1 MiB). Cheap first pass: files with equal
     * partial hashes are candidates; only candidates get fully hashed.
     */
    public static String partialSha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] size = {0, 0, 0, 0, 0, 0, 0, 0};
            long s = Files.size(file);
            for (int i = 7; i >= 0; i--) {
                size[i] = (byte) (s & 0xff);
                s >>>= 8;
            }
            digest.update(size);
            byte[] buffer = new byte[PARTIAL_BYTES];
            int read = in.read(buffer);
            if (read > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
