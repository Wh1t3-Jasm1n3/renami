package app.renami.rename;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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
}
