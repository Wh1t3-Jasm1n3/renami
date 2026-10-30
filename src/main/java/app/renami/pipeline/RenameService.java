package app.renami.pipeline;

import app.renami.format.FormatEngine;
import app.renami.media.MediaScanner;
import app.renami.parse.EpisodeNumber;
import app.renami.parse.FileNameParser;
import app.renami.parse.ReleaseInfo;
import app.renami.rename.RenameOperation;
import app.renami.similarity.NameScorer;
import app.renami.web.Datasource;
import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The UI-independent rename pipeline:
 * scan → parse → group → datasource lookup → score → format → plan.
 * Shared by the CLI and the Swing GUI.
 */
public final class RenameService {

    public record Options(String mode, String format, double minScore) {
    }

    public record Item(Path file, ReleaseInfo info) {
    }

    public record Match(Item item, ShowInfo show, EpisodeInfo episode) {
    }

    private final Datasource datasource;
    private final PrintStream log;

    public RenameService(Datasource datasource, PrintStream log) {
        this.datasource = datasource;
        this.log = log;
    }

    public List<Item> parseAll(List<Path> files) {
        return files.stream()
                .map(f -> new Item(f, FileNameParser.parse(f.getFileName().toString(), MediaScanner.extension(f))))
                .toList();
    }

    public List<RenameOperation> plan(List<Path> files, Options options) throws Exception {
        List<Item> items = parseAll(files);
        String mode = options.mode();
        if (mode.equalsIgnoreCase("local")) {
            return planLocal(items, options);
        }
        boolean episodeMode = mode.equalsIgnoreCase("episode")
                || (mode.equalsIgnoreCase("auto") && items.stream().anyMatch(i -> i.info().hasEpisode()));
        return episodeMode ? planEpisodes(items, options) : planMovies(items, options);
    }

    /** Episode mode: group by title guess, one lookup per show, per-episode titles from the API. */
    public List<RenameOperation> planEpisodes(List<Item> items, Options options) throws Exception {
        Map<String, List<Item>> byShow = new LinkedHashMap<>();
        for (Item item : items) {
            if (item.info().hasEpisode()) {
                byShow.computeIfAbsent(NameScorer.canonical(item.info().titleGuess()), k -> new ArrayList<>()).add(item);
            }
        }

        List<RenameOperation> plan = new ArrayList<>();
        for (List<Item> group : byShow.values()) {
            String titleGuess = group.get(0).info().titleGuess();
            ShowInfo show = bestShow(titleGuess, options.minScore());
            if (show == null) {
                log.println("skip (no confident match for \"" + titleGuess + "\", best score < " + options.minScore() + ")");
                continue;
            }
            log.println("match: \"" + titleGuess + "\"  →  " + show.name()
                    + (show.year() > 0 ? " (" + show.year() + ")" : ""));

            String template = options.format() != null ? options.format() : "{n} - {s00e00} - {t}";
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

    public List<RenameOperation> planMovies(List<Item> items, Options options) throws Exception {
        List<RenameOperation> plan = new ArrayList<>();
        String template = options.format() != null ? options.format() : "{n} ({y})";
        for (Item item : items) {
            List<MovieInfo> candidates = datasource.searchMovie(item.info().titleGuess(), item.info().year());
            MovieInfo best = candidates.stream()
                    .max(Comparator.comparingDouble(c -> scoreMovie(item.info(), c)))
                    .orElse(null);
            if (best == null || scoreMovie(item.info(), best) < options.minScore()) {
                log.println("skip (no confident match for \"" + item.info().titleGuess() + "\")");
                continue;
            }
            log.println("match: \"" + item.info().titleGuess() + "\"  →  " + best.title() + " (" + best.year() + ")");
            Map<String, Object> vars = FormatEngine.standardVars(best.title(), best.year(), null, null, null, null);
            String name = sanitize(FormatEngine.format(template, vars));
            Path target = item.file().resolveSibling(name + "." + MediaScanner.extension(item.file()));
            plan.add(new RenameOperation(item.file(), target));
        }
        return plan;
    }

    /** Local mode: no network — rename purely from facts parsed out of the filename. */
    public List<RenameOperation> planLocal(List<Item> items, Options options) {
        List<RenameOperation> plan = new ArrayList<>();
        for (Item item : items) {
            ReleaseInfo info = item.info();
            String template;
            Map<String, Object> vars;
            if (info.hasEpisode()) {
                template = options.format() != null ? options.format() : "{n} - {s00e00}";
                EpisodeNumber ep = info.episode();
                vars = FormatEngine.standardVars(info.titleGuess(), info.year(), ep.format(), null, ep.season(), ep.episode());
            } else {
                template = options.format() != null ? options.format() : "{n} ({y})";
                vars = FormatEngine.standardVars(info.titleGuess(), info.year(), null, null, null, null);
            }
            String name = sanitize(FormatEngine.format(template, vars));
            Path target = item.file().resolveSibling(name + "." + MediaScanner.extension(item.file()));
            plan.add(new RenameOperation(item.file(), target));
        }
        return plan;
    }

    public ShowInfo bestShow(String titleGuess, double minScore) throws Exception {
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
    public static String sanitize(String name) {
        String s = name.replaceAll("[/\\p{Cntrl}]", "-").trim();
        s = s.replaceAll("\\s{2,}", " ");
        s = s.replaceAll("[. ]+$", "");
        return s.isEmpty() ? "untitled" : s;
    }
}
