package app.renami.cli;

import app.renami.format.FormatEngine;
import app.renami.media.MediaScanner;
import app.renami.parse.EpisodeNumber;
import app.renami.parse.FileNameParser;
import app.renami.parse.ReleaseInfo;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.RenameExecutor;
import app.renami.rename.RenameOperation;
import app.renami.similarity.NameScorer;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;
import app.renami.web.tmdb.TmdbClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "rename", description = "Match media files against TMDb and rename them.", mixinStandardHelpOptions = true)
public class RenameCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "INPUT", description = "media file or folder")
    Path input;

    @Option(names = "--apply", description = "perform the rename (default is dry-run)")
    boolean apply;

    @Option(names = "--mode", defaultValue = "auto", description = "auto | movie | episode (default: ${DEFAULT-VALUE})")
    String mode;

    @Option(names = "--format", description = "custom Groovy-backed format, e.g. \"{n} - {s00e00} - {t}\"")
    String format;

    @Option(names = "--conflict", defaultValue = "FAIL", description = "FAIL | SKIP | OVERWRITE | INDEX (default: ${DEFAULT-VALUE})")
    ConflictPolicy conflict;

    @Option(names = "--hardlink", description = "create hardlinks instead of moving files")
    boolean hardlink;

    @Option(names = "--min-score", defaultValue = "0.60", description = "minimum match score 0..1 (default: ${DEFAULT-VALUE})")
    double minScore;

    private record Item(Path file, ReleaseInfo info) {
    }

    /** Overridable for tests and future --db options. */
    Datasource createDatasource(Config config) {
        String token = config.get("tmdb.api.key");
        if (token == null) {
            throw new IllegalStateException("no TMDb API key. Set RENAMI_TMDB_API_KEY or tmdb.api.key in ~/.renami/config.properties");
        }
        return new TmdbClient(token);
    }

    HistoryStore createHistoryStore() {
        return new HistoryStore();
    }

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

        List<Path> files = MediaScanner.scan(input);
        if (files.isEmpty()) {
            System.err.println("no media files found under " + input);
            return 1;
        }
        List<Item> items = files.stream()
                .map(f -> new Item(f, FileNameParser.parse(f.getFileName().toString(), MediaScanner.extension(f))))
                .toList();

        boolean episodeMode = mode.equalsIgnoreCase("episode")
                || (mode.equalsIgnoreCase("auto") && items.stream().anyMatch(i -> i.info().hasEpisode()));

        List<RenameOperation> plan = episodeMode
                ? planEpisodes(datasource, items)
                : planMovies(datasource, items);

        if (plan.isEmpty()) {
            System.out.println("nothing to do");
            return 0;
        }

        RenameExecutor executor = new RenameExecutor(conflict, hardlink);
        if (!apply) {
            for (RenameOperation op : plan) {
                System.out.println("[dry-run] " + op.source().getFileName() + "  →  " + op.target().getFileName());
            }
            System.out.println();
            System.out.println(plan.size() + " operations planned. Re-run with --apply to execute.");
            return 0;
        }

        List<RenameExecutor.Result> results = executor.apply(plan);
        List<RenameOperation> applied = results.stream()
                .filter(r -> r.status().equals("renamed") || r.status().equals("hardlinked"))
                .map(RenameExecutor.Result::operation)
                .toList();
        createHistoryStore().record(applied);
        for (RenameExecutor.Result r : results) {
            System.out.println("[" + r.status() + "] " + r.operation().source().getFileName() + "  →  " + r.actualTarget().getFileName());
        }
        System.out.println();
        System.out.println(applied.size() + " operations applied. Use `renami undo` to revert.");
        return 0;
    }

    private List<RenameOperation> planEpisodes(Datasource datasource, List<Item> items) throws Exception {
        Map<String, List<Item>> byShow = new LinkedHashMap<>();
        for (Item item : items) {
            if (item.info().hasEpisode()) {
                byShow.computeIfAbsent(NameScorer.canonical(item.info().titleGuess()), k -> new ArrayList<>()).add(item);
            }
        }

        List<RenameOperation> plan = new ArrayList<>();
        for (List<Item> group : byShow.values()) {
            String titleGuess = group.get(0).info().titleGuess();
            ShowInfo show = bestShow(datasource, titleGuess);
            if (show == null) {
                System.err.println("skip (no confident match for \"" + titleGuess + "\", best score < " + minScore + ")");
                continue;
            }
            System.err.println("match: \"" + titleGuess + "\"  →  " + show.name()
                    + (show.year() > 0 ? " (" + show.year() + ")" : ""));

            String template = format != null ? format : "{n} - {s00e00} - {t}";
            for (Item item : group) {
                EpisodeNumber ep = item.info().episode();
                String episodeTitle = "";
                for (EpisodeInfo info : datasource.getSeason(show.id(), ep.season())) {
                    if (info.episode() == ep.episode()) {
                        episodeTitle = info.name() == null ? "" : info.name();
                        break;
                    }
                }
                Map<String, Object> vars = FormatEngine.standardVars(
                        show.name(), show.year(), ep.format(), episodeTitle, ep.season(), ep.episode());
                String name = sanitize(FormatEngine.format(template, vars));
                Path target = item.file().resolveSibling(name + "." + MediaScanner.extension(item.file()));
                plan.add(new RenameOperation(item.file(), target));
            }
        }
        return plan;
    }

    private List<RenameOperation> planMovies(Datasource datasource, List<Item> items) throws Exception {
        List<RenameOperation> plan = new ArrayList<>();
        String template = format != null ? format : "{n} ({y})";
        for (Item item : items) {
            List<MovieInfo> candidates = datasource.searchMovie(item.info().titleGuess(), item.info().year());
            MovieInfo best = candidates.stream()
                    .max(Comparator.comparingDouble(c -> scoreMovie(item.info(), c)))
                    .orElse(null);
            if (best == null || scoreMovie(item.info(), best) < minScore) {
                System.err.println("skip (no confident match for \"" + item.info().titleGuess() + "\")");
                continue;
            }
            System.err.println("match: \"" + item.info().titleGuess() + "\"  →  " + best.title() + " (" + best.year() + ")");
            Map<String, Object> vars = FormatEngine.standardVars(best.title(), best.year(), null, null, null, null);
            String name = sanitize(FormatEngine.format(template, vars));
            Path target = item.file().resolveSibling(name + "." + MediaScanner.extension(item.file()));
            plan.add(new RenameOperation(item.file(), target));
        }
        return plan;
    }

    private ShowInfo bestShow(Datasource datasource, String titleGuess) throws Exception {
        List<ShowInfo> candidates = datasource.searchShow(titleGuess, 0);
        return candidates.stream()
                .max(Comparator.comparingDouble(c -> NameScorer.scoreRaw(titleGuess, c.name())))
                .filter(c -> NameScorer.scoreRaw(titleGuess, c.name()) >= minScore)
                .orElse(null);
    }

    private static double scoreMovie(ReleaseInfo info, MovieInfo movie) {
        double score = NameScorer.scoreRaw(info.titleGuess(), movie.title());
        if (info.year() > 0 && info.year() == movie.year()) {
            score = Math.min(1.0, score + 0.15);
        }
        return score;
    }

    /** Remove characters that are illegal or risky in file names. */
    static String sanitize(String name) {
        String s = name.replaceAll("[/\\p{Cntrl}]", "-").trim();
        s = s.replaceAll("\\s{2,}", " ");
        s = s.replaceAll("[. ]+$", "");
        return s.isEmpty() ? "untitled" : s;
    }
}
