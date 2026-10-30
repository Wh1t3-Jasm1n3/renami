package app.renami.cli;

import app.renami.rename.HistoryStore;
import app.renami.rename.RenameOperation;
import app.renami.rename.TransferAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndoCommandTest {

    @TempDir
    Path dir;

    private UndoCommand commandFor(HistoryStore store) {
        return new UndoCommand() {
            @Override
            HistoryStore createHistoryStore() {
                return store;
            }
        };
    }

    @Test
    void undoOfCopyBatchDeletesTargetAndKeepsSource() throws Exception {
        HistoryStore store = new HistoryStore(dir.resolve("history.jsonl"));
        Path source = Files.writeString(dir.resolve("movie.mkv"), "x");
        Path target = Files.writeString(dir.resolve("Movie (1).mkv"), "x");
        store.record(List.of(new RenameOperation(source, target)), TransferAction.COPY);

        String out = run(commandFor(store));
        assertTrue(out.contains("[deleted ]"));
        assertFalse(Files.exists(target), "the copy must be removed");
        assertTrue(Files.exists(source), "the source must survive");
    }

    @Test
    void undoOfCopyBatchRefusesWhenSourceIsGone() throws Exception {
        HistoryStore store = new HistoryStore(dir.resolve("history.jsonl"));
        Path source = dir.resolve("movie.mkv"); // never created
        Path target = Files.writeString(dir.resolve("copy.mkv"), "only copy");
        store.record(List.of(new RenameOperation(source, target)), TransferAction.COPY);

        String out = run(commandFor(store));
        assertTrue(out.contains("[kept    ]"));
        assertTrue(Files.exists(target), "must not delete the only remaining copy");
    }

    @Test
    void undoOfSymlinkBatchDeletesOnlyTheLink() throws Exception {
        HistoryStore store = new HistoryStore(dir.resolve("history.jsonl"));
        Path source = Files.writeString(dir.resolve("movie.mkv"), "x");
        Path link = dir.resolve("movie-link.mkv");
        Files.createSymbolicLink(link, source.toAbsolutePath());
        store.record(List.of(new RenameOperation(source, link)), TransferAction.SYMLINK);

        run(commandFor(store));
        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS), "link removed");
        assertTrue(Files.isRegularFile(source), "linked source untouched");
    }

    @Test
    void undoOfRenameBatchStillMovesBack() throws Exception {
        HistoryStore store = new HistoryStore(dir.resolve("history.jsonl"));
        Path from = dir.resolve("old.mkv");
        Path to = Files.writeString(dir.resolve("new.mkv"), "x");
        store.record(List.of(new RenameOperation(from, to)), TransferAction.RENAME);

        String out = run(commandFor(store));
        assertTrue(out.contains("[restored]"));
        assertTrue(Files.exists(from));
        assertFalse(Files.exists(to));
    }

    private static String run(UndoCommand command) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            assertEquals(0, command.call());
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
