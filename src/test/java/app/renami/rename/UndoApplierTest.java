package app.renami.rename;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndoApplierTest {

    @TempDir
    Path dir;

    @Test
    void chainedRenamesAreRevertedInReverseOrder() throws Exception {
        // a.mkv → b.mkv, then b.mkv → c.mkv: only reverse order puts everything back.
        HistoryStore.Batch batch = new HistoryStore.Batch("x", 0, false, List.of(
                new String[]{dir.resolve("a.mkv").toString(), dir.resolve("b.mkv").toString(), "renamed"},
                new String[]{dir.resolve("b.mkv").toString(), dir.resolve("c.mkv").toString(), "renamed"}));
        Files.writeString(dir.resolve("c.mkv"), "x");

        List<UndoApplier.Outcome> outcomes = UndoApplier.apply(batch);
        assertEquals(2, UndoApplier.restoredCount(outcomes));
        assertTrue(Files.exists(dir.resolve("a.mkv")), "file ends back at the original name");
        assertFalse(Files.exists(dir.resolve("b.mkv")));
        assertFalse(Files.exists(dir.resolve("c.mkv")));
    }

    @Test
    void keptOutcomesDoNotCountAsRestored() throws Exception {
        Path source = dir.resolve("gone.mkv"); // never created
        Path copy = Files.writeString(dir.resolve("only-copy.mkv"), "x");
        HistoryStore.Batch batch = new HistoryStore.Batch("y", 0, false, List.<String[]>of(
                new String[]{source.toString(), copy.toString(), "copied"}));

        List<UndoApplier.Outcome> outcomes = UndoApplier.apply(batch);
        assertEquals("kept", outcomes.get(0).label());
        assertEquals(0, UndoApplier.restoredCount(outcomes));
        assertTrue(Files.exists(copy), "the only copy must survive");
    }
}
