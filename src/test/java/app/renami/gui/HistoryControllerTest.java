package app.renami.gui;

import app.renami.rename.HistoryStore;
import app.renami.rename.RenameOperation;
import app.renami.rename.TransferAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryControllerTest {

    @TempDir
    Path dir;

    private HistoryController controllerFor(HistoryStore store) {
        return new HistoryController(store);
    }

    @Test
    void listsBatchesNewestFirst() throws Exception {
        HistoryStore store = new HistoryStore(dir.resolve("history.jsonl"));
        store.record(List.of(new RenameOperation(dir.resolve("a.mkv"), dir.resolve("A.mkv"))));
        store.record(List.of(new RenameOperation(dir.resolve("b.mkv"), dir.resolve("B.mkv"))));
        List<HistoryStore.Batch> batches = controllerFor(store).batches();
        assertEquals(2, batches.size());
        assertTrue(batches.get(0).time() >= batches.get(1).time());
    }

    @Test
    void undoRestoresChosenBatchAndMarksItUndone() throws Exception {
        HistoryStore store = new HistoryStore(dir.resolve("history.jsonl"));
        Path from = dir.resolve("old.mkv");
        Path to = Files.writeString(dir.resolve("new.mkv"), "x");
        store.record(List.of(new RenameOperation(from, to)), TransferAction.RENAME);

        HistoryController.UndoReport report = controllerFor(store).undo(store.batches().get(0).id());
        assertEquals(1, report.restored());
        assertEquals(1, report.total());
        assertTrue(Files.exists(from), "file moved back");
        assertFalse(Files.exists(to));
        assertTrue(store.batches().get(0).undone(), "journal marks the batch undone");
    }

    @Test
    void undoingTwiceIsRefused() throws Exception {
        HistoryStore store = new HistoryStore(dir.resolve("history.jsonl"));
        store.record(List.of(new RenameOperation(dir.resolve("a.mkv"), dir.resolve("A.mkv"))));
        String id = store.batches().get(0).id();
        controllerFor(store).undo(id);

        assertThrows(IllegalStateException.class, () -> controllerFor(store).undo(id));
    }

    @Test
    void unknownBatchIdIsRejected() {
        HistoryController controller = controllerFor(new HistoryStore(dir.resolve("history.jsonl")));
        assertThrows(IllegalArgumentException.class, () -> controller.undo("nope"));
    }
}
