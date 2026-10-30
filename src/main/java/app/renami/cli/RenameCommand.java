package app.renami.cli;

import app.renami.media.CompanionFiles;
import app.renami.media.MediaScanner;
import app.renami.parse.FileNameParser;
import app.renami.pipeline.RenameService;
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

@Command(name = "rename", description = "Match media files against a metadata database and rename them.", mixinStandardHelpOptions = true)
public class RenameCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "INPUT", description = "media file or folder")
    Path input;

    @Option(names = "--apply", description = "perform the rename (default is dry-run)")
    boolean apply;

    @Option(names = "--mode", defaultValue = "auto", description = "auto | movie | episode | local (default: ${DEFAULT-VALUE})")
    String mode = "auto";

    @Option(names = "--db", defaultValue = "auto", description = "auto | tmdb | tvdb | omdb | kitsu | local (default: ${DEFAULT-VALUE})")
    String db = "auto";

    @Option(names = "--format", description = "custom Groovy-backed format, e.g. \"{n} - {s00e00} - {t}\"")
    String format;

    @Option(names = "--conflict", defaultValue = "FAIL", description = "FAIL | SKIP | OVERWRITE | INDEX (default: ${DEFAULT-VALUE})")
    ConflictPolicy conflict = ConflictPolicy.FAIL;

    @Option(names = "--action", defaultValue = "rename",
            description = "transfer mode: " + TransferAction.CHOICES + " (default: ${DEFAULT-VALUE})")
    String action = "rename";

    @Option(names = "--hardlink", description = "deprecated alias for --action hardlink")
    boolean hardlink;

    @Option(names = "--min-score", defaultValue = "0.60", description = "minimum match score 0..1 (default: ${DEFAULT-VALUE})")
    double minScore = 0.60;

    @Option(names = "--order", defaultValue = "default", description = "episode order: default | aired | dvd | absolute (anime) (default: ${DEFAULT-VALUE})")
    String order = "default";

    @Option(names = "--no-cache", description = "bypass the on-disk HTTP response cache")
    boolean noCache;

    @Option(names = {"--companion", "--no-companion"}, defaultValue = "true",
            description = "rename sidecars that share the media base name (subs, nfo, artwork) along with it; "
                    + "--no-companion disables (default: ${DEFAULT-VALUE})")
    boolean companion = true;

    @Option(names = "--script", description = "Groovy script to run for each processed file (vars: fn, info, source, renami)")
    Path script;

    @Option(names = "--def", description = "define a script variable, e.g. --def myVar=value (repeatable)")
    List<String> definitions = new ArrayList<>();

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
        if (hardlink && transferAction == TransferAction.RENAME) {
            System.err.println("warning: --hardlink is deprecated, use --action hardlink");
            transferAction = TransferAction.HARDLINK;
        }

        List<Path> files = MediaScanner.scan(input);
        if (files.isEmpty()) {
            System.err.println("no media files found under " + input);
            return 1;
        }

        RenameService service = new RenameService(datasource, System.err);
        List<RenameOperation> plan = service.plan(files, new RenameService.Options(mode, format, minScore, order));
        if (plan.isEmpty()) {
            System.out.println("nothing to do");
            return 0;
        }
        if (companion) {
            plan = CompanionFiles.expand(plan);
        }

        RenameExecutor executor = new RenameExecutor(conflict, transferAction);
        if (!apply) {
            for (RenameOperation op : plan) {
                System.out.println("[dry-run] " + op.source().getFileName() + "  →  " + op.target().getFileName());
            }
            System.out.println();
            System.out.println("action: " + transferAction.name().toLowerCase()
                    + (transferAction.consumesSource() ? " (source is moved)" : " (source is kept)"));
            System.out.println();
            System.out.println(plan.size() + " operations planned. Re-run with --apply to execute.");
            return 0;
        }

        List<RenameExecutor.Result> results = executor.apply(plan);
        List<RenameOperation> applied = results.stream()
                .filter(RenameExecutor.Result::applied)
                .map(RenameExecutor.Result::operation)
                .toList();
        createHistoryStore().record(applied, transferAction);
        for (RenameExecutor.Result r : results) {
            System.out.println("[" + r.status() + "] " + r.operation().source().getFileName() + "  →  " + r.actualTarget().getFileName());
        }
        runScripts(results);
        System.out.println();
        System.out.println(applied.size() + " operations applied. Use `renami undo` to revert.");
        return 0;
    }

    private void runScripts(List<RenameExecutor.Result> results) throws Exception {
        if (script == null) {
            return;
        }
        ScriptRuntime runtime = new ScriptRuntime(ScriptRuntime.parseDefs(definitions));
        for (RenameExecutor.Result r : results) {
            if (!r.applied()) {
                continue;
            }
            Path source = r.operation().source();
            runtime.run(script, Map.of(
                    "fn", r.actualTarget().toFile(),
                    "info", FileNameParser.parse(source.getFileName().toString(), MediaScanner.extension(source)),
                    "source", input.toFile()));
        }
    }

    /** Overridable for tests and datasource injection. */
    Datasource createDatasource(Config config) {
        return Datasources.create(config, db, noCache);
    }

    HistoryStore createHistoryStore() {
        return new HistoryStore();
    }
}
