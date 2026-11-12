package app.renami.gui;

import app.renami.media.MediaScanner;
import app.renami.parse.FileNameParser;
import app.renami.parse.ReleaseInfo;
import app.renami.similarity.NameScorer;
import app.renami.web.opensubtitles.OpenSubtitlesClient;
import app.renami.web.opensubtitles.OpenSubtitlesClient.Candidate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * UI-independent subtitle logic: search the best candidate per file,
 * then download on demand. Mirrors {@link app.renami.cli.SubtitlesCommand}.
 */
public final class SubtitlesController {

    public record Row(Path source, Path target, OpenSubtitlesClient.Candidate candidate) {
    }

    private final OpenSubtitlesClient client;

    public SubtitlesController(OpenSubtitlesClient client) {
        this.client = client;
    }

    public List<Row> preview(List<Path> files, String lang) throws IOException {
        List<Row> rows = new ArrayList<>();
        for (Path file : files) {
            ReleaseInfo info = FileNameParser.parse(file.getFileName().toString(), MediaScanner.extension(file));
            Candidate best = bestByHash(file, lang);
            if (best == null) {
                best = bestByRelevance(client.search(searchQuery(info), lang), info.titleGuess());
            }
            if (best != null) {
                rows.add(new Row(file, subtitlePath(file, best), best));
            }
        }
        return rows;
    }

    /** Exact moviehash match first; falls back to filename query search. */
    private Candidate bestByHash(Path file, String lang) {
        try {
            if (!app.renami.hash.OpenSubtitlesHash.isHashable(file)) {
                return null;
            }
            return best(client.searchByHash(app.renami.hash.OpenSubtitlesHash.of(file), lang));
        } catch (Exception e) {
            return null; // hash search is best-effort; query search covers the rest
        }
    }

    /** Search string: title plus SxxEyy for episodes, plus year for movies. */
    static String searchQuery(ReleaseInfo info) {
        StringBuilder query = new StringBuilder(info.titleGuess() == null ? "" : info.titleGuess());
        if (info.hasEpisode()) {
            query.append(' ').append(info.episode().format());
        } else if (info.year() > 0) {
            query.append(' ').append(info.year());
        }
        return query.toString().trim();
    }

    /** Rank query hits by title similarity to the file, download count breaks ties. */
    static Candidate bestByRelevance(List<Candidate> candidates, String titleGuess) {
        String guess = titleGuess == null ? "" : titleGuess;
        return candidates.stream()
                .max(Comparator.comparingDouble((Candidate c) ->
                                NameScorer.scoreRaw(guess, c.title() == null ? "" : c.title()))
                        .thenComparingInt(Candidate::downloads))
                .orElse(null);
    }

    private static Candidate best(List<Candidate> candidates) {
        return candidates.stream().max(Comparator.comparingInt(Candidate::downloads)).orElse(null);
    }

    public void download(Row row) throws IOException {
        client.download(row.candidate().id(), row.target());
    }

    static Path subtitlePath(Path video, OpenSubtitlesClient.Candidate best) {
        String base = video.getFileName().toString();
        int dot = base.lastIndexOf('.');
        String stem = dot < 0 ? base : base.substring(0, dot);
        String ext = best.format() != null && !best.format().isBlank() ? best.format() : "srt";
        return video.resolveSibling(stem + "." + best.language() + "." + ext);
    }
}
