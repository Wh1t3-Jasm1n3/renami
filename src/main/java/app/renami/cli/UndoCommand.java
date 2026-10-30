package app.renami.cli;

import app.renami.rename.HistoryStore;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "undo", description = "Revert the most recent applied rename batch.", mixinStandardHelpOptions = true)
public class UndoCommand implements Callable<Integer> {

    HistoryStore createHistoryStore() {
        return new HistoryStore();
    }

    @Override
    public Integer call() throws Exception {
        HistoryStore history = createHistoryStore();
        var batchOpt = history.lastActiveBatch();
        if (batchOpt.isEmpty()) {
            System.out.println("nothing to undo");
            return 0;
        }
        HistoryStore.Batch batch = batchOpt.get();
        int restored = 0;
        // reverse order so later moves are reverted first
        for (int i = batch.ops().size() - 1; i >= 0; i--) {
            String[] op = batch.ops().get(i);
            Path from = Path.of(op[0]); // original location
            Path to = Path.of(op[1]);   // current location
            if (Files.exists(to) && !Files.exists(from)) {
                Files.move(to, from);
                restored++;
                System.out.println("[restored] " + to.getFileName() + "  →  " + from.getFileName());
            } else {
                System.out.println("[skipped ] " + to + " (missing or target occupied)");
            }
        }
        history.markUndone(batch.id());
        System.out.println();
        System.out.println(restored + " of " + batch.ops().size() + " files restored.");
        return 0;
    }
}
