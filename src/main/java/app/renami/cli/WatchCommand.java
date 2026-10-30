package app.renami.cli;

import app.renami.media.CompanionFiles;
import app.renami.pipeline.RenameService;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.RenameExecutor;
import app.renami.rename.RenameOperation;
import app.renami.rename.TransferAction;
import app.renami.util.Config;
import app.renami.watch.FolderWatcher;
import app.renami.web.Datasource;
import app.renami.web.Datasources;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Folder watch (the classic {@code -watch}): new media files are processed as soon as
 * their size stops changing, through the same match → format → transfer pipeline as
 * {@code rename}.
 */
@Command(name = "watch", description = "Watch a folder and process new media files as they land.", mixinStandardHelpOptions = true)
public class WatchCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "FOLDER", description = "folder to watch (subfolders included)")
    Path input;

    @Option(names = "--apply", description = "perform the transfer (default is dry-run)")
    boolean apply;

    @Option(names = "--mode", defaultValue = "auto", description = "auto | movie | episode | local (default: ${DEFAULT-VALUE})")
    String mode = "auto";

    @Option(names = "--db", defaultValue = "auto", description = "auto | tmdb | tvdb | omdb | kitsu | local (default: ${DEFAULT-VALUE})")
    String db = "auto";

    @Option(names = "--format", description = "custom Groovy-backed format, e.g. \"{n} - {s00e00} - {t}\"")
    String format;

    @Option(names = "--conflict", defaultValue = "INDEX", description = "FAIL | SKIP | OVERWRITE | INDEX (default: ${DEFAULT-VALUE})")
    ConflictPolicy conflict = ConflictPolicy.INDEX;

    @Option(names = "--action", defaultValue = "rename",
            description = "transfer mode: " + TransferAction.CHOICES + " (default: ${DEFAULT-VALUE})")
    String action = "rename";

    @Option(names = "--min-score", defaultValue = "0.60", description = "minimum match score 0..1 (default: ${DEFAULT-VALUE})")
    double minScore = 0.60;

    @Option(names = "--order", defaultValue = "default", description = "episode order: default | aired | dvd | absolute (anime) (default: ${DEFAULT-VALUE})")
    String order = "default";

    @Option(names = {"--companion", "--no-companion"}, defaultValue = "true",
            description = "move sidecars that share the media base name along with it; "
                    + "--no-companion disables (default: ${DEFAULT-VALUE})")
    boolean companion = true;

    @Option(names = "--settle", defaultValue = "2",
            description = "seconds a file's size must stay unchanged before it is processed (default: ${DEFAULT-VALUE})")
    int settleSeconds = 2;

    private volatile FolderWatcher watcher;

    @Override
    public Integer call() throws Exception {
        Config config = Config.load();
        Datasource datasource;
        try {
            datasource = createDatasource(config);
        } catch (IllegalStateException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }
        TransferAction transferAction;
        try {
            transferAction = TransferAction.parse(action);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }

        RenameService service = new RenameService(datasource, System.err);
        HistoryStore history = createHistoryStore();
        System.out.println("watching " + input.toAbsolutePath() + " — press Ctrl+C to stop");
        try (FolderWatcher w = new FolderWatcher(input, Duration.ofSeconds(settleSeconds))) {
            this.watcher = w;
            Runtime.getRuntime().addShutdownHook(new Thread(w::stop));
            w.awaitBatches(batch -> {
                try {
                    processBatch(service, history, batch, transferAction);
                } catch (Exception e) {
                    System.err.println("error: " + e.getMessage());
                }
            });
        }
        return 0;
    }

    void processBatch(RenameService service, HistoryStore history, List<Path> files,
                      TransferAction transferAction) throws Exception {
        System.out.println();
        System.out.println("settled: " + files.stream().map(p -> p.getFileName().toString()).toList());
        List<RenameOperation> plan = service.plan(files, new RenameService.Options(mode, format, minScore, order));
        if (plan.isEmpty()) {
            System.out.println("nothing to do");
            return;
        }
        if (companion) {
            plan = CompanionFiles.expand(plan);
        }
        if (!apply) {
            for (RenameOperation op : plan) {
                System.out.println("[dry-run] " + op.source().getFileName() + "  →  " + op.target().getFileName());
            }
            return;
        }
        List<RenameExecutor.Result> results = new RenameExecutor(conflict, transferAction).apply(plan);
        List<RenameOperation> applied = results.stream()
                .filter(RenameExecutor.Result::applied)
                .map(RenameExecutor.Result::operation)
                .toList();
        history.record(applied, transferAction);
        for (RenameExecutor.Result r : results) {
            System.out.println("[" + r.status() + "] " + r.operation().source().getFileName()
                    + "  →  " + r.actualTarget().getFileName());
        }
        System.out.println(applied.size() + " operations applied. Use `renami undo` to revert.");
    }

    /** Stops the watch loop (used by tests; Ctrl+C is handled by the shutdown hook). */
    void stop() {
        FolderWatcher w = watcher;
        if (w != null) {
            w.stop();
        }
    }

    /** Overridable for tests and datasource injection. */
    Datasource createDatasource(Config config) {
        return Datasources.create(config, db, false);
    }

    HistoryStore createHistoryStore() {
        return new HistoryStore();
    }
}
