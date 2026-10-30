package app.renami.hash;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.CRC32;

/** Hash algorithms used for verification files and duplicate detection. */
public enum HashType {

    CRC32("CRC-32", 8),
    MD5("MD5", 32),
    SHA1("SHA-1", 40),
    SHA256("SHA-256", 64);

    private final String algorithm;
    private final int hexLength;

    HashType(String algorithm, int hexLength) {
        this.algorithm = algorithm;
        this.hexLength = hexLength;
    }

    public int hexLength() {
        return hexLength;
    }

    public static HashType parse(String name) {
        String key = name.toUpperCase().replace("-", "");
        return switch (key) {
            case "CRC32" -> CRC32;
            case "MD5" -> MD5;
            case "SHA1" -> SHA1;
            case "SHA256", "SHA" -> SHA256;
            default -> throw new IllegalArgumentException("unknown hash algorithm: " + name + " (use crc32|md5|sha1|sha256)");
        };
    }

    /** Detect the likely algorithm from a hex digest length. */
    public static HashType byHexLength(int length) {
        for (HashType type : values()) {
            if (type.hexLength == length) {
                return type;
            }
        }
        return null;
    }

    public String hex(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            if (this == CRC32) {
                CRC32 crc = new CRC32();
                in.transferTo(new java.io.OutputStream() {
                    @Override
                    public void write(int b) {
                        crc.update(b);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        crc.update(b, off, len);
                    }
                });
                return String.format("%08x", crc.getValue());
            }
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("hash algorithm unavailable: " + algorithm, e);
        }
    }
}
