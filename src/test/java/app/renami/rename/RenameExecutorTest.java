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

        List<RenameExecutor.Result> results = new RenameExecutor(ConflictPolicy.INDEX, TransferAction.RENAME).apply(plan);
        assertEquals("renamed", results.get(0).status());
        assertEquals("renamed", results.get(1).status());
        assertEquals("a-renamed (2).txt", results.get(1).actualTarget().getFileName().toString());
        assertTrue(Files.exists(dir.resolve("a-renamed.txt")));
        assertTrue(Files.exists(dir.resolve("a-renamed (2).txt")));
    }

    @Test
    void caseOnlyRenameOnDisk() throws Exception {
        Path f = Files.writeString(dir.resolve("movie.mkv"), "x");
        new RenameExecutor(ConflictPolicy.FAIL, TransferAction.RENAME)
                .apply(List.of(new RenameOperation(f, dir.resolve("Movie.mkv"))));
        assertTrue(Files.exists(dir.resolve("Movie.mkv")));
    }

    @Test
    void hardlinkKeepsSource() throws Exception {
        Path f = Files.writeString(dir.resolve("src.mkv"), "x");
        var result = new RenameExecutor(ConflictPolicy.FAIL, TransferAction.HARDLINK)
                .apply(List.of(new RenameOperation(f, dir.resolve("dst.mkv")))).get(0);
        assertEquals("hardlinked", result.status());
        assertTrue(Files.exists(f));
        assertEquals(2, Files.getAttribute(dir.resolve("dst.mkv"), "unix:nlink"));
    }

    @Test
    void copyKeepsSourceAndDuplicatesContent() throws Exception {
        Path f = Files.writeString(dir.resolve("src.mkv"), "payload");
        var result = new RenameExecutor(ConflictPolicy.FAIL, TransferAction.COPY)
                .apply(List.of(new RenameOperation(f, dir.resolve("dst.mkv")))).get(0);
        assertEquals("copied", result.status());
        assertEquals("payload", Files.readString(f));
        assertEquals("payload", Files.readString(dir.resolve("dst.mkv")));
    }

    @Test
    void symlinkPointsAtAbsoluteSource() throws Exception {
        Path f = Files.writeString(dir.resolve("src.mkv"), "x");
        var result = new RenameExecutor(ConflictPolicy.FAIL, TransferAction.SYMLINK)
                .apply(List.of(new RenameOperation(f, dir.resolve("dst.mkv")))).get(0);
        assertEquals("symlinked", result.status());
        Path link = dir.resolve("dst.mkv");
        assertTrue(Files.isSymbolicLink(link));
        assertEquals(f.toAbsolutePath(), Files.readSymbolicLink(link));
        assertEquals("x", Files.readString(link)); // follows the link
    }

    @Test
    void copyOntoCaseOnlyTargetIsRefused() throws Exception {
        Path f = Files.writeString(dir.resolve("movie.mkv"), "x");
        var result = new RenameExecutor(ConflictPolicy.FAIL, TransferAction.COPY)
                .apply(List.of(new RenameOperation(f, dir.resolve("Movie.mkv")))).get(0);
        assertEquals("skipped (same file)", result.status());
        assertTrue(Files.exists(dir.resolve("movie.mkv")));
    }

    @Test
    void moveAcrossFoldersConsumesSource() throws Exception {
        Path sub = Files.createDirectories(dir.resolve("out"));
        Path f = Files.writeString(dir.resolve("src.mkv"), "x");
        var result = new RenameExecutor(ConflictPolicy.FAIL, TransferAction.MOVE)
                .apply(List.of(new RenameOperation(f, sub.resolve("dst.mkv")))).get(0);
        assertEquals("moved", result.status());
        assertTrue(Files.exists(sub.resolve("dst.mkv")));
        assertTrue(!Files.exists(f));
    }
}
