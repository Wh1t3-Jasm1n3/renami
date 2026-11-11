package app.renami.cli;

import app.renami.rename.HistoryStore;
import app.renami.rename.UndoApplier;
import picocli.CommandLine.Command;

import java.util.List;
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
        List<UndoApplier.Outcome> outcomes = UndoApplier.apply(batch);
        for (UndoApplier.Outcome outcome : outcomes) {
            System.out.println(outcome.message());
        }
        history.markUndone(batch.id());
        System.out.println();
        System.out.println(UndoApplier.restoredCount(outcomes) + " of " + batch.ops().size() + " files restored.");
        return 0;
    }
}
