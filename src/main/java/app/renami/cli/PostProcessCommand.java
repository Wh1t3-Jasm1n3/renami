package app.renami.cli;

import app.renami.archive.ArchiveTool;
import app.renami.media.MediaScanner;
import app.renami.parse.FileNameParser;
import app.renami.pipeline.RenameService;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.RenameExecutor;
import app.renami.rename.RenameOperation;
import app.renami.script.ScriptRuntime;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.Datasources;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

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

    @Option(names = "--db", defaultValue = "auto", description = "auto | tmdb | tvdb | omdb | local (default: ${DEFAULT-VALUE})")
    String db = "auto";

    @Option(names = "--conflict", defaultValue = "INDEX", description = "FAIL | SKIP | OVERWRITE | INDEX (default: ${DEFAULT-VALUE})")
    ConflictPolicy conflict = ConflictPolicy.INDEX;

    @Option(names = "--min-score", defaultValue = "0.60", description = "minimum match score 0..1 (default: ${DEFAULT-VALUE})")
    double minScore = 0.60;

    @Option(names = "--order", defaultValue = "default", description = "episode order: default | aired | dvd (default: ${DEFAULT-VALUE})")
    String order = "default";

    @Option(names = "--clean", description = "delete archives after their contents were processed")
    boolean clean;

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

        List<Path> archives = findArchives(input);
        List<Path> candidates = new ArrayList<>(MediaScanner.scan(input));
        List<Path> extractedDirs = new ArrayList<>();
        for (Path archive : archives) {
            Path extractDir = input.resolve(".renami-extract").resolve(stripExtension(archive));
            try {
                ArchiveTool.extract(archive, extractDir);
                extractedDirs.add(extractDir);
                candidates.addAll(MediaScanner.scan(extractDir));
                System.out.println("extracted: " + archive.getFileName());
            } catch (IOException e) {
                System.err.println("skip archive " + archive.getFileName() + ": " + e.getMessage());
            }
        }
        if (candidates.isEmpty()) {
            System.err.println("no media files found under " + input);
            return 1;
        }

        RenameService service = new RenameService(datasource, System.err);
        List<RenameService.Item> items = service.parseAll(candidates);
        boolean episodeMode = mode.equalsIgnoreCase("episode")
                || (mode.equalsIgnoreCase("auto") && items.stream().anyMatch(i -> i.info().hasEpisode()));
        String template = episodeMode ? tvFormat : movieFormat;
        RenameService.Options options = new RenameService.Options(mode, template, minScore, order);
        RenameService.TargetResolver resolver = output != null
                ? RenameService.structuredResolver(template, output.toAbsolutePath())
                : null;
        List<RenameOperation> plan = episodeMode
                ? service.planEpisodes(items, options, resolver)
                : service.planMovies(items, options, resolver);

        if (plan.isEmpty()) {
            cleanup(extractedDirs);
            System.out.println("nothing to do");
            return 0;
        }
        if (!apply) {
            for (RenameOperation op : plan) {
                System.out.println("[dry-run] " + relativize(op.source()) + "  →  " + op.target().getFileName());
            }
            System.out.println();
            System.out.println(plan.size() + " operations planned. Re-run with --apply to execute.");
            cleanup(extractedDirs);
            return 0;
        }

        RenameExecutor executor = new RenameExecutor(conflict, false);
        List<RenameExecutor.Result> results = executor.apply(plan);
        List<RenameOperation> applied = results.stream()
                .filter(r -> r.status().equals("renamed"))
                .map(RenameExecutor.Result::operation)
                .toList();
        createHistoryStore().record(applied);
        for (RenameExecutor.Result r : results) {
            System.out.println("[" + r.status() + "] " + relativize(r.operation().source()) + "  →  " + r.actualTarget());
        }
        runScripts(results);
        if (clean) {
            deleteArchives(archives, results);
        }
        cleanup(extractedDirs);
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
            if (!r.status().equals("renamed")) {
                continue;
            }
            Path source = r.operation().source();
            runtime.run(script, Map.of(
                    "fn", r.actualTarget().toFile(),
                    "info", FileNameParser.parse(source.getFileName().toString(), MediaScanner.extension(source)),
                    "source", input.toFile(),
                    "target", (output != null ? output : source.getParent()).toFile()));
        }
    }

    /** Only delete an archive when everything extracted from it was processed successfully. */
    private void deleteArchives(List<Path> archives, List<RenameExecutor.Result> results) throws IOException {
        boolean allProcessed = results.stream().allMatch(r -> r.status().equals("renamed") || r.status().equals("unchanged"));
        if (!allProcessed) {
            System.err.println("--clean: keeping archives (not all contents processed)");
            return;
        }
        for (Path archive : archives) {
            Files.deleteIfExists(archive);
            System.out.println("[clean] deleted " + archive.getFileName());
        }
    }

    private String relativize(Path file) {
        Path absolute = file.toAbsolutePath();
        Path base = input.toAbsolutePath();
        return absolute.startsWith(base) ? base.relativize(absolute).toString() : absolute.toString();
    }

    private static List<Path> findArchives(Path input) throws IOException {
        if (!Files.isDirectory(input)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(input)) {
            return stream.filter(ArchiveTool::isArchive).toList();
        }
    }

    private static void cleanup(List<Path> dirs) throws IOException {
        for (Path dir : dirs) {
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
        Path parent = dirs.isEmpty() ? null : dirs.get(0).getParent();
        if (parent != null) {
            Files.deleteIfExists(parent); // .renami-extract
        }
    }

    private static String stripExtension(Path archive) {
        String name = archive.getFileName().toString();
        for (String compound : List.of(".tar.gz", ".tar.bz2", ".tar.xz")) {
            if (name.toLowerCase().endsWith(compound)) {
                return name.substring(0, name.length() - compound.length());
            }
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** Overridable for tests and datasource injection. */
    Datasource createDatasource(Config config) {
        return Datasources.create(config, db, false);
    }

    HistoryStore createHistoryStore() {
        return new HistoryStore();
    }
}
