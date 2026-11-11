package app.renami.rename;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reverts one journal batch: renamed/moved files are moved back, created
 * copies/links are deleted — refusing when the source is gone. Shared by
 * {@code renami undo} and the GUI history panel.
 */
public final class UndoApplier {

    /** One per-op outcome; label is restored|deleted|kept|skipped, message is the display line. */
    public record Outcome(String label, String message) {

        /** True when the undo actually changed something on disk. */
        public boolean changed() {
            return label.equals("restored") || label.equals("deleted");
        }
    }

    private UndoApplier() {
    }

    /** Applies the undo in reverse order so later transfers are reverted first. */
    public static List<Outcome> apply(HistoryStore.Batch batch) throws IOException {
        List<Outcome> outcomes = new ArrayList<>();
        for (int i = batch.ops().size() - 1; i >= 0; i--) {
            String[] op = batch.ops().get(i);
            Path from = Path.of(op[0]); // original location
            Path to = Path.of(op[1]);   // location the transfer created
            if (TransferAction.consumesSource(op.length > 2 ? op[2] : "renamed")) {
                if (Files.exists(to) && !Files.exists(from)) {
                    Files.move(to, from);
                    outcomes.add(new Outcome("restored",
                            "[restored] " + to.getFileName() + "  →  " + from.getFileName()));
                } else {
                    outcomes.add(new Outcome("skipped", "[skipped ] " + to + " (missing or target occupied)"));
                }
            } else if (Files.exists(to, LinkOption.NOFOLLOW_LINKS) && Files.exists(from, LinkOption.NOFOLLOW_LINKS)) {
                // copy/hardlink/symlink left the source in place: undo means removing the extra entry.
                Files.delete(to);
                outcomes.add(new Outcome("deleted",
                        "[deleted ] " + to.getFileName() + " (source kept at " + from.getFileName() + ")"));
            } else if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) {
                outcomes.add(new Outcome("kept",
                        "[kept    ] " + to + " (source is gone, refusing to delete the only copy)"));
            } else {
                outcomes.add(new Outcome("skipped", "[skipped ] " + to + " (missing)"));
            }
        }
        return outcomes;
    }

    /** How many of the outcomes actually restored or removed something. */
    public static long restoredCount(List<Outcome> outcomes) {
        return outcomes.stream().filter(Outcome::changed).count();
    }
}
