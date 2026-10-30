package app.renami.rename;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenameExecutorTest {

    @TempDir
    Path dir;

    @Test
    void renamesAndHandlesConflicts() throws Exception {
        Path a = Files.writeString(dir.resolve("a.txt"), "a");
        Path b = Files.writeString(dir.resolve("b.txt"), "b");
        Path targetB = dir.resolve("a-renamed.txt");

        List<RenameOperation> plan = List.of(
                new RenameOperation(a, dir.resolve("a-renamed.txt")),
                new RenameOperation(b, targetB)); // conflict: a-renamed.txt now exists

        List<RenameExecutor.Result> results = new RenameExecutor(ConflictPolicy.INDEX, false).apply(plan);
        assertEquals("renamed", results.get(0).status());
        assertEquals("renamed", results.get(1).status());
        assertEquals("a-renamed (2).txt", results.get(1).actualTarget().getFileName().toString());
        assertTrue(Files.exists(dir.resolve("a-renamed.txt")));
        assertTrue(Files.exists(dir.resolve("a-renamed (2).txt")));
    }

    @Test
    void caseOnlyRenameOnDisk() throws Exception {
        Path f = Files.writeString(dir.resolve("movie.mkv"), "x");
        new RenameExecutor(ConflictPolicy.FAIL, false)
                .apply(List.of(new RenameOperation(f, dir.resolve("Movie.mkv"))));
        assertTrue(Files.exists(dir.resolve("Movie.mkv")));
    }

    @Test
    void hardlinkKeepsSource() throws Exception {
        Path f = Files.writeString(dir.resolve("src.mkv"), "x");
        new RenameExecutor(ConflictPolicy.FAIL, true)
                .apply(List.of(new RenameOperation(f, dir.resolve("dst.mkv"))));
        assertTrue(Files.exists(f));
        assertTrue(Files.exists(dir.resolve("dst.mkv")));
    }
}
