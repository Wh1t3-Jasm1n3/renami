package app.renami.cli;

import app.renami.gui.WatchController;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.TransferAction;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.Datasources;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Folder watch (the classic {@code -watch}): new media files are processed as soon as
 * their size stops changing, through the same match → format → transfer pipeline as
 * {@code rename}. Runs on the shared {@link WatchController} that the GUI uses too.
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

    private volatile WatchController controller;

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
        try {
            TransferAction.parse(action);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }

        WatchController.Options options = new WatchController.Options(
                mode, format, action, conflict, minScore, order, companion, apply);
        WatchController c = new WatchController(createHistoryStore());
        this.controller = c;
        System.out.println("watching " + input.toAbsolutePath() + " — press Ctrl+C to stop");
        Runtime.getRuntime().addShutdownHook(new Thread(c::stop));
        c.run(input, settleSeconds, options, datasource, System.out::println);
        return 0;
    }

    /** Stops the watch loop (used by tests; Ctrl+C is handled by the shutdown hook). */
    void stop() {
        WatchController c = controller;
        if (c != null) {
            c.stop();
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
