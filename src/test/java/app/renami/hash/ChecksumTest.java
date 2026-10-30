package app.renami.hash;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksumTest {

    @TempDir
    Path dir;

    @Test
    void hashesMatchKnownVectors() throws Exception {
        Path file = dir.resolve("hello.txt");
        Files.writeString(file, "hello world\n");
        Files.writeString(dir.resolve("nospace.txt"), "hello world");
        // well-known digests for "hello world\n"
        assertEquals("6f5902ac237024bdd0c176cb93063dc4", HashType.MD5.hex(file));
        assertEquals("2aae6c35c94fcfb415dbe95f408b9ce91ee846ed", HashType.SHA1.hex(dir.resolve("nospace.txt")));
        assertEquals(64, HashType.SHA256.hex(file).length());
        assertEquals(8, HashType.CRC32.hex(file).length());
    }

    @Test
    void detectsAlgorithmByDigestLength() {
        assertEquals(HashType.CRC32, HashType.byHexLength(8));
        assertEquals(HashType.MD5, HashType.byHexLength(32));
        assertEquals(HashType.SHA1, HashType.byHexLength(40));
        assertEquals(HashType.SHA256, HashType.byHexLength(64));
        assertEquals(null, HashType.byHexLength(13));
    }

    @Test
    void sfvRoundTrip() throws Exception {
        Path file = dir.resolve("movie.mkv");
        Files.writeString(file, "content");
        String hash = HashType.MD5.hex(file);

        Path sfv = dir.resolve("checksums.sfv");
        VerificationFile.write(sfv, List.of(new VerificationFile.Entry("movie.mkv", hash)));
        assertEquals("movie.mkv " + hash.toUpperCase(), Files.readString(sfv).trim());

        List<VerificationFile.Entry> read = VerificationFile.read(sfv);
        assertEquals(1, read.size());
        assertEquals("movie.mkv", read.get(0).name());
        assertEquals(hash, read.get(0).hash());
    }

    @Test
    void readsBothSfvAndMd5sumFormats() throws Exception {
        Path sfv = dir.resolve("mixed.txt");
        Files.writeString(sfv, """
                ; comment line
                file1.mkv 0123456789abcdef0123456789abcdef
                89abcdef0123456789abcdef0123456789abcdef  file2.mkv
                deadbeefdeadbeefdeadbeefdeadbeefdeadbeef *file3.bin
                # not a checksum line
                """);
        List<VerificationFile.Entry> entries = VerificationFile.read(sfv);
        assertEquals(3, entries.size());
        assertEquals("file1.mkv", entries.get(0).name());
        assertEquals("file2.mkv", entries.get(1).name());
        assertEquals("file3.bin", entries.get(2).name()); // binary-mode marker stripped
    }

    @Test
    void partialHashMatchesOnlyForIdenticalPrefixes() throws Exception {
        Path a = dir.resolve("a.bin");
        Path b = dir.resolve("b.bin");
        byte[] payload = new byte[2048];
        java.util.Arrays.fill(payload, (byte) 7);
        Files.write(a, payload);
        Files.write(b, payload);
        assertEquals(PartialHasher.partialSha256(a), PartialHasher.partialSha256(b));

        Files.write(b, new byte[2047]); // different size → different partial hash
        assertTrue(!PartialHasher.partialSha256(a).equals(PartialHasher.partialSha256(b)));
    }
}
