package app.renami.cli;

import app.renami.gui.PostProcessController;
import app.renami.media.MediaScanner;
import app.renami.parse.FileNameParser;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.RenameExecutor;
import app.renami.rename.RenameOperation;
import app.renami.rename.TransferAction;
import app.renami.script.ScriptRuntime;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.Datasources;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * One-shot workflow: scan a folder (including archives), match against a
 * datasource, rename, optionally move into a structured output tree, run
 * Groovy hooks, and clean up processed archives.
 */
@Command(name = "post-process", description = "Match, rename and organize media in one pass (archives included).", mixinStandardHelpOptions = true)
public class PostProcessCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "INPUT", description = "folder with media files and/or archives")
    Path input;

    @Option(names = {"-o", "--output"}, description = "structured output root (default: rename in place)")
    Path output;

    @Option(names = "--tv-format", defaultValue = "{n}/Season {s}/{n} - {s00e00} - {t}",
            description = "episode format; '/' creates folders (default: ${DEFAULT-VALUE})")
    String tvFormat = "{n}/Season {s}/{n} - {s00e00} - {t}";

    @Option(names = "--movie-format", defaultValue = "{n} ({y})/{n} ({y})",
            description = "movie format; '/' creates folders (default: ${DEFAULT-VALUE})")
    String movieFormat = "{n} ({y})/{n} ({y})";

    @Option(names = "--mode", defaultValue = "auto", description = "auto | movie | episode | local (default: ${DEFAULT-VALUE})")
    String mode = "auto";

    @Option(names = "--db", defaultValue = "auto", description = "auto | tmdb | tvmaze | omdb | kitsu | local (default: ${DEFAULT-VALUE})")
    String db = "auto";

    @Option(names = "--conflict", defaultValue = "INDEX", description = "FAIL | SKIP | OVERWRITE | INDEX (default: ${DEFAULT-VALUE})")
    ConflictPolicy conflict = ConflictPolicy.INDEX;

    @Option(names = "--action", defaultValue = "rename",
            description = "transfer mode: " + TransferAction.CHOICES + " (default: ${DEFAULT-VALUE})")
    String action = "rename";

    @Option(names = "--min-score", defaultValue = "0.60", description = "minimum match score 0..1 (default: ${DEFAULT-VALUE})")
    double minScore = 0.60;

    @Option(names = "--order", defaultValue = "default", description = "episode order: default | aired | dvd | absolute (anime) (default: ${DEFAULT-VALUE})")
    String order = "default";

    @Option(names = "--clean", description = "delete archives after their contents were processed")
    boolean clean;

    @Option(names = {"--companion", "--no-companion"}, defaultValue = "true",
            description = "move sidecars that share the media base name (subs, nfo, artwork) along with it; "
                    + "--no-companion disables (default: ${DEFAULT-VALUE})")
    boolean companion = true;

    @Option(names = "--script", description = "Groovy script run for each processed file (vars: fn, info, source, target, renami)")
    Path script;

    @Option(names = "--def", description = "define a script variable, e.g. --def myVar=value (repeatable)")
    List<String> definitions = new ArrayList<>();

    @Option(names = "--apply", description = "perform the move (default is dry-run)")
    boolean apply;

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
            TransferAction.parse(action); // validate before touching any archive
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }

        PostProcessController.Options options = new PostProcessController.Options(
                mode, tvFormat, movieFormat, action, conflict, minScore, order, clean, companion);
        PostProcessController controller = new PostProcessController(createHistoryStore());
        PostProcessController.Preview preview;
        try {
            preview = controller.plan(input, output, options, datasource, System.out::println);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            return 1;
        }

        if (preview.plan().isEmpty()) {
            controller.discard(preview);
            System.out.println("nothing to do");
            return 0;
        }
        if (!apply) {
            for (RenameOperation op : preview.plan()) {
                System.out.println("[dry-run] " + relativize(op.source()) + "  →  " + op.target().getFileName());
            }
            System.out.println();
            System.out.println(preview.plan().size() + " operations planned. Re-run with --apply to execute.");
            controller.discard(preview);
            return 0;
        }

        ScriptRuntime runtime = script == null ? null
                : new ScriptRuntime(ScriptRuntime.parseDefs(definitions));
        List<RenameExecutor.Result> results = controller.apply(preview, options, r -> {
            System.out.println("[" + r.status() + "] " + relativize(r.operation().source()) + "  →  " + r.actualTarget());
            if (runtime != null && r.applied()) {
                runScript(runtime, r);
            }
        }, System.out::println);

        long applied = results.stream().filter(RenameExecutor.Result::applied).count();
        System.out.println();
        System.out.println(applied + " operations applied. Use `renami undo` to revert.");
        return 0;
    }

    private void runScript(ScriptRuntime runtime, RenameExecutor.Result r) {
        Path source = r.operation().source();
        try {
            runtime.run(script, Map.of(
                    "fn", r.actualTarget().toFile(),
                    "info", FileNameParser.parse(source.getFileName().toString(), MediaScanner.extension(source)),
                    "source", input.toFile(),
                    "target", (output != null ? output : source.getParent()).toFile()));
        } catch (Exception e) {
            throw new RuntimeException("script failed for " + source.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private String relativize(Path file) {
        Path absolute = file.toAbsolutePath();
        Path base = input.toAbsolutePath();
        return absolute.startsWith(base) ? base.relativize(absolute).toString() : absolute.toString();
    }

    /** Overridable for tests and datasource injection. */
    Datasource createDatasource(Config config) {
        return Datasources.create(config, db, false);
    }

    HistoryStore createHistoryStore() {
        return new HistoryStore();
    }
}
