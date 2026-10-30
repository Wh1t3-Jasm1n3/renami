package app.renami.hash;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * OpenSubtitles 64-bit movie hash: file size plus the signed little-endian
 * 64-bit word sums of the first 64 KiB and the final 8 bytes of the file.
 */
public final class OpenSubtitlesHash {

    private static final int BLOCK = 65536;

    private OpenSubtitlesHash() {
    }

    public static boolean isHashable(Path file) throws IOException {
        return Files.size(file) >= BLOCK;
    }

    public static String hex(Path file) throws IOException {
        return String.format("%016x", of(file));
    }

    public static long of(Path file) throws IOException {
        long size = Files.size(file);
        if (size < BLOCK) {
            throw new IOException("file too small for a moviehash: " + size + " bytes");
        }
        long sum = size;
        byte[] block = new byte[BLOCK];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.readNBytes(block, 0, BLOCK);
            for (int i = 0; i + 8 <= read; i += 8) {
                sum += word(block, i);
            }
        }
        // final 8 bytes of the file (the last word of the reference algorithm's tail block)
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer last8 = ByteBuffer.allocate(8);
            int read = 0;
            while (read < 8) {
                int r = ch.read(last8, size - 8 + read);
                if (r < 0) {
                    break;
                }
                read += r;
            }
            sum += word(last8.array(), 0);
        }
        return sum;
    }

    private static long word(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }
}
