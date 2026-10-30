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

    public record Options(String mode, String format, double minScore, String order) {

        public Options(String mode, String format, double minScore) {
            this(mode, format, minScore, "default");
        }
    }

    /** Computes the destination path for a file given its format variables. */
    public interface TargetResolver {
        Path resolve(Path file, Map<String, Object> vars);
    }

    /**
     * Structured resolver for post-processing: the template may contain '/' to
     * create folder hierarchies under outputRoot, e.g. "{n}/Season {s}/{n} - {s00e00}".
     */
    public static TargetResolver structuredResolver(String template, Path outputRoot) {
        return (file, vars) -> {
            StringBuilder relative = new StringBuilder();
            for (String segment : FormatEngine.format(template, vars).split("/")) {
                String clean = sanitize(segment);
                if (!clean.isEmpty()) {
                    if (relative.length() > 0) {
                        relative.append('/');
                    }
                    relative.append(clean);
                }
            }
            return outputRoot.resolve(relative + "." + MediaScanner.extension(file));
        };
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
        return plan(files, options, null);
    }

    public List<RenameOperation> plan(List<Path> files, Options options, TargetResolver resolver) throws Exception {
        List<Item> items = parseAll(files);
        String mode = options.mode();
        if (mode.equalsIgnoreCase("local") || (mode.equalsIgnoreCase("auto") && datasource.networkFree())) {
            return planLocal(items, options, resolver);
        }
        boolean episodeMode = mode.equalsIgnoreCase("episode")
                || (mode.equalsIgnoreCase("auto") && items.stream()
                .anyMatch(i -> i.info().hasEpisode() || i.info().hasAbsoluteEpisode()));
        return episodeMode ? planEpisodes(items, options, resolver) : planMovies(items, options, resolver);
    }

    /** Episode mode: group by title guess, one lookup per show, per-episode titles from the API. */
    public List<RenameOperation> planEpisodes(List<Item> items, Options options) throws Exception {
        return planEpisodes(items, options, null);
    }

    public List<RenameOperation> planEpisodes(List<Item> items, Options options, TargetResolver resolver) throws Exception {
        boolean absoluteMode = "absolute".equalsIgnoreCase(options.order());
        Map<String, List<Item>> byShow = new LinkedHashMap<>();
        for (Item item : items) {
            if (item.info().hasEpisode() || (absoluteMode && item.info().hasAbsoluteEpisode())) {
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
            List<EpisodeInfo> absoluteList = null;
            for (Item item : group) {
                EpisodeNumber ep = item.info().episode();
                String episodeTitle = "";
                if (ep == null) {
                    // absolute numbering (anime): map absolute number → season/episode via the API
                    if (absoluteList == null) {
                        absoluteList = datasource.getSeason(show.id(), 1, "absolute");
                    }
                    EpisodeInfo mapped = null;
                    for (EpisodeInfo info : absoluteList) {
                        if (info.episode() == item.info().absoluteEpisode()) {
                            mapped = info;
                            break;
                        }
                    }
                    if (mapped == null) {
                        log.println("skip (" + item.file().getFileName() + ": absolute episode "
                                + item.info().absoluteEpisode() + " not found for \"" + show.name() + "\")");
                        continue;
                    }
                    ep = new EpisodeNumber(mapped.season(), mapped.episode());
                    episodeTitle = mapped.name() == null ? "" : mapped.name();
                } else {
                    for (EpisodeInfo info : datasource.getSeason(show.id(), ep.season(), options.order())) {
                        if (info.season() == ep.season() && info.episode() == ep.episode()) {
                            episodeTitle = info.name() == null ? "" : info.name();
                            break;
                        }
                    }
                }
                Map<String, Object> vars = FormatEngine.standardVars(
                        show.name(), show.year(), ep.format(), episodeTitle, ep.season(), ep.episode());
                plan.add(new RenameOperation(item.file(), targetFor(item.file(), vars, template, resolver)));
            }
        }
        return plan;
    }

    public List<RenameOperation> planMovies(List<Item> items, Options options) throws Exception {
        return planMovies(items, options, null);
    }

    public List<RenameOperation> planMovies(List<Item> items, Options options, TargetResolver resolver) throws Exception {
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
            plan.add(new RenameOperation(item.file(), targetFor(item.file(), vars, template, resolver)));
        }
        return plan;
    }

    /** Local mode: no network — rename purely from facts parsed out of the filename. */
    public List<RenameOperation> planLocal(List<Item> items, Options options) {
        return planLocal(items, options, null);
    }

    public List<RenameOperation> planLocal(List<Item> items, Options options, TargetResolver resolver) {
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
            plan.add(new RenameOperation(item.file(), targetFor(item.file(), vars, template, resolver)));
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

    private static Path targetFor(Path file, Map<String, Object> vars, String template, TargetResolver resolver) {
        if (resolver != null) {
            return resolver.resolve(file, vars);
        }
        String name = sanitize(FormatEngine.format(template, vars));
        return file.resolveSibling(name + "." + MediaScanner.extension(file));
    }

    /** Remove characters that are illegal or risky in file names, and collapse empty format artifacts. */
    public static String sanitize(String name) {
        String s = name.replaceAll("[/\\p{Cntrl}]", "-");
        s = s.replaceAll("\\(\\s*\\)", " ");   // empty {y}/{t} leave bare parens
        s = s.replaceAll("\\s{2,}", " ").trim();
        s = s.replaceAll("[\\s._-]+$", "");
        return s.isEmpty() ? "untitled" : s;
    }
}
