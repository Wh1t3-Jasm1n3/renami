package app.renami.rename;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryStoreTest {

    @TempDir
    Path dir;

    @Test
    void recordsAndUndoesBatches() throws Exception {
        Path file = dir.resolve("history.jsonl");
        HistoryStore store = new HistoryStore(file);

        Path a = dir.resolve("a.mkv");
        Path b = dir.resolve("b.mkv");
        store.record(java.util.List.of(
                new RenameOperation(a, dir.resolve("show - s01e01.mkv")),
                new RenameOperation(b, dir.resolve("show - s01e02.mkv"))));

        Optional<HistoryStore.Batch> batch = store.lastActiveBatch();
        assertTrue(batch.isPresent());
        assertEquals(2, batch.get().ops().size());

        store.markUndone(batch.get().id());
        assertTrue(store.lastActiveBatch().isEmpty(), "batch must not be returned after undo");
    }

    @Test
    void recordsTransferVerb() throws Exception {
        HistoryStore store = new HistoryStore(dir.resolve("history.jsonl"));
        store.record(java.util.List.of(new RenameOperation(dir.resolve("a.mkv"), dir.resolve("b.mkv"))),
                TransferAction.HARDLINK);
        assertEquals("hardlinked", store.lastActiveBatch().orElseThrow().ops().get(0)[2]);
    }

    @Test
    void legacyTwoElementOpsReadAsRenamed() throws Exception {
        Path file = dir.resolve("history.jsonl");
        Files.writeString(file, "{\"id\":\"x\",\"time\":1,\"undone\":false,\"ops\":[[\"/a\",\"/b\"]]}\n");
        assertEquals("renamed", new HistoryStore(file).lastActiveBatch().orElseThrow().ops().get(0)[2]);
    }

    @Test
    void batchesListsAllNewestFirstWithLatestRecordPerId() throws Exception {
        HistoryStore store = new HistoryStore(dir.resolve("history.jsonl"));
        store.record(java.util.List.of(new RenameOperation(dir.resolve("a.mkv"), dir.resolve("A.mkv"))));
        store.record(java.util.List.of(new RenameOperation(dir.resolve("b.mkv"), dir.resolve("B.mkv"))));
        List<HistoryStore.Batch> all = store.batches();
        assertEquals(2, all.size());
        assertTrue(all.get(0).time() >= all.get(1).time(), "newest batch first");
        assertTrue(all.stream().noneMatch(HistoryStore.Batch::undone));

        store.markUndone(all.get(0).id());
        List<HistoryStore.Batch> reread = store.batches();
        assertEquals(2, reread.size(), "undone batches stay listed");
        assertTrue(reread.get(0).undone(), "the undone flag comes from the newest record for the id");
    }
}
