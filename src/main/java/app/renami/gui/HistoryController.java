package app.renami.gui;

import app.renami.rename.HistoryStore;
import app.renami.rename.UndoApplier;

import java.io.IOException;
import java.util.List;

/**
 * UI-independent history logic: browse the rename journal and undo a chosen batch.
 * Mirrors {@link app.renami.cli.UndoCommand}, but for any batch instead of just the last.
 */
public final class HistoryController {

    public record UndoReport(int restored, int total, List<UndoApplier.Outcome> outcomes) {
    }

    private final HistoryStore store;

    public HistoryController(HistoryStore store) {
        this.store = store;
    }

    /** All batches, newest first. */
    public List<HistoryStore.Batch> batches() throws IOException {
        return store.batches();
    }

    /** Reverts the batch with this id and marks it undone in the journal. */
    public UndoReport undo(String batchId) throws IOException {
        HistoryStore.Batch batch = batches().stream()
                .filter(b -> b.id().equals(batchId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown batch " + batchId));
        if (batch.undone()) {
            throw new IllegalStateException("this batch was already undone");
        }
        List<UndoApplier.Outcome> outcomes = UndoApplier.apply(batch);
        store.markUndone(batchId);
        return new UndoReport((int) UndoApplier.restoredCount(outcomes), batch.ops().size(), outcomes);
    }
}
