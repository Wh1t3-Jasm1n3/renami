package app.renami.gui;

import app.renami.media.CompanionFiles;
import app.renami.pipeline.RenameService;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.RenameExecutor;
import app.renami.rename.RenameOperation;
import app.renami.rename.TransferAction;
import app.renami.watch.FolderWatcher;
import app.renami.web.Datasource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * UI-independent folder watch: new media files are processed as soon as their
 * size stops changing, through the same match → format → transfer pipeline as
 * {@code rename}. Mirrors {@link app.renami.cli.WatchCommand}; the CLI runs on
 * this same controller.
 */
public final class WatchController {

    public record Options(String mode, String format, String action, ConflictPolicy conflict,
                          double minScore, String order, boolean companion, boolean apply) {
    }

    private final HistoryStore history;
    private volatile FolderWatcher watcher;

    public WatchController(HistoryStore history) {
        this.history = history;
    }

    /** Blocks until {@link #stop()} is called, processing settled batches as they land. */
    public void run(Path input, int settleSeconds, Options options, Datasource datasource,
                    Consumer<String> log) throws Exception {
        RenameService service = new RenameService(datasource, System.err);
        TransferAction transferAction = TransferAction.parse(options.action());
        try (FolderWatcher w = new FolderWatcher(input, Duration.ofSeconds(settleSeconds))) {
            this.watcher = w;
            w.awaitBatches(batch -> {
                try {
                    processBatch(service, options, transferAction, batch, log);
                } catch (Exception e) {
                    log.accept("error: " + e.getMessage());
                }
            });
        }
    }

    /** Stops the watch loop. */
    public void stop() {
        FolderWatcher w = watcher;
        if (w != null) {
            w.stop();
        }
    }

    private void processBatch(RenameService service, Options options, TransferAction transferAction,
                              List<Path> files, Consumer<String> log) throws Exception {
        log.accept("settled: " + files.stream().map(p -> p.getFileName().toString()).toList());
        List<RenameOperation> plan = service.plan(files,
                new RenameService.Options(options.mode(), options.format(), options.minScore(), options.order()));
        if (plan.isEmpty()) {
            log.accept("nothing to do");
            return;
        }
        if (options.companion()) {
            plan = CompanionFiles.expand(plan);
        }
        if (!options.apply()) {
            for (RenameOperation op : plan) {
                log.accept("[dry-run] " + op.source().getFileName() + "  →  " + op.target().getFileName());
            }
            return;
        }
        List<RenameExecutor.Result> results = new RenameExecutor(options.conflict(), transferAction).apply(plan);
        List<RenameOperation> applied = results.stream()
                .filter(RenameExecutor.Result::applied)
                .map(RenameExecutor.Result::operation)
                .toList();
        history.record(applied, transferAction);
        for (RenameExecutor.Result r : results) {
            log.accept("[" + r.status() + "] " + r.operation().source().getFileName()
                    + "  →  " + r.actualTarget().getFileName());
        }
        log.accept(applied.size() + " operations applied. Use `renami undo` to revert.");
    }
}
