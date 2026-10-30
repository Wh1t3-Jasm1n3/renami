package app.renami.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DupeCommandTest {

    @TempDir
    Path dir;

    @Test
    void findsExactDuplicatesAcrossFolders() throws Exception {
        byte[] payload = new byte[4096];
        java.util.Arrays.fill(payload, (byte) 42);
        Files.createDirectories(dir.resolve("nested"));
        Files.write(dir.resolve("a.mkv"), payload);
        Files.write(dir.resolve("nested/b.mkv"), payload);
        Files.write(dir.resolve("unique.mkv"), new byte[4096]); // same size, different content
        Files.writeString(dir.resolve("small.txt"), "tiny");

        List<List<Path>> groups = DupeCommand.findDuplicates(dir);
        assertEquals(1, groups.size());
        assertEquals(List.of(dir.resolve("a.mkv"), dir.resolve("nested/b.mkv")), groups.get(0));
    }

    @Test
    void sameFileDifferentSizeIsNotDuplicate() throws Exception {
        Files.write(dir.resolve("a.mkv"), new byte[]{1, 2, 3});
        Files.write(dir.resolve("b.mkv"), new byte[]{1, 2, 3, 4});
        assertEquals(0, DupeCommand.findDuplicates(dir).size());
    }
}
