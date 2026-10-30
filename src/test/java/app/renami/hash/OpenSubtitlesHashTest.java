package app.renami.hash;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSubtitlesHashTest {

    @TempDir
    Path dir;

    /** Independent reference implementation of the documented algorithm. */
    private static long referenceHash(byte[] data) {
        long sum = data.length;
        ByteBuffer head = ByteBuffer.wrap(data, 0, Math.min(data.length, 65536)).order(ByteOrder.LITTLE_ENDIAN);
        while (head.remaining() >= 8) {
            sum += head.getLong();
        }
        ByteBuffer tail = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        tail.position(data.length - 65536 + (65536 - 8)); // last 8 bytes of the file
        sum += tail.getLong();
        return sum;
    }

    @Test
    void matchesReferenceOnLargeFile() throws Exception {
        byte[] data = new byte[100_000];
        new Random(42).nextBytes(data);
        Path file = dir.resolve("movie.mkv");
        Files.write(file, data);

        assertEquals(referenceHash(data), OpenSubtitlesHash.of(file));
        assertEquals(String.format("%016x", referenceHash(data)), OpenSubtitlesHash.hex(file));
    }

    @Test
    void exactly64KiBFileHashes() throws Exception {
        byte[] data = new byte[65536];
        new Random(7).nextBytes(data);
        Path file = dir.resolve("edge.mkv");
        Files.write(file, data);
        assertEquals(referenceHash(data), OpenSubtitlesHash.of(file));
    }

    @Test
    void smallFilesAreRejected() throws Exception {
        Path file = Files.writeString(dir.resolve("tiny.mkv"), "too small");
        assertTrue(!OpenSubtitlesHash.isHashable(file));
        assertThrows(java.io.IOException.class, () -> OpenSubtitlesHash.of(file));
    }
}
