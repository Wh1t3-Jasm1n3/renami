package app.renami.cli;

import app.renami.media.MediaScanner;
import app.renami.parse.FileNameParser;
import app.renami.util.Config;
import app.renami.web.HttpJson;
import app.renami.web.opensubtitles.OpenSubtitlesClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Subtitle mode: find matching subtitles for each media file and save them
 * next to the video as {@code <video>.<lang>.srt}.
 */
@Command(name = "subtitles", description = "Download matching subtitles for media files.", mixinStandardHelpOptions = true)
public class SubtitlesCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "INPUT", description = "media file or folder")
    Path input;

    @Option(names = "--lang", defaultValue = "en", description = "subtitle language code(s), comma separated (default: ${DEFAULT-VALUE})")
    String lang;

    @Option(names = "--apply", description = "download the best match (default is dry-run)")
    boolean apply;

    @Override
    public Integer call() throws Exception {
        OpenSubtitlesClient client;
        try {
            client = createClient(Config.load());
        } catch (IllegalStateException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }

        List<Path> files = MediaScanner.scan(input);
        if (files.isEmpty()) {
            System.err.println("no media files found under " + input);
            return 1;
        }

        int found = 0;
        for (Path file : files) {
            String query = FileNameParser.parse(file.getFileName().toString(), MediaScanner.extension(file))
                    .titleGuess();
            List<OpenSubtitlesClient.Candidate> candidates = client.search(query, lang);
            OpenSubtitlesClient.Candidate best = candidates.stream()
                    .max(Comparator.comparingInt(OpenSubtitlesClient.Candidate::downloads))
                    .orElse(null);
            if (best == null) {
                System.out.println("[none]   " + file.getFileName());
                continue;
            }
            Path target = subtitlePath(file, best);
            if (apply) {
                client.download(best.id(), target);
                System.out.println("[saved]  " + file.getFileName() + "  →  " + target.getFileName()
                        + "  (" + best.language() + ", " + best.downloads() + " downloads)");
            } else {
                System.out.println("[found]  " + file.getFileName() + "  →  " + target.getFileName()
                        + "  (\"" + best.title() + "\", " + best.language() + ", " + best.downloads() + " downloads)");
            }
            found++;
        }
        System.out.println();
        System.out.println(found + " of " + files.size() + " files have a subtitle match"
                + (apply ? "." : ". Re-run with --apply to download."));
        return 0;
    }

    static Path subtitlePath(Path video, OpenSubtitlesClient.Candidate best) {
        String base = video.getFileName().toString();
        int dot = base.lastIndexOf('.');
        String stem = dot < 0 ? base : base.substring(0, dot);
        String ext = best.format() != null && !best.format().isBlank() ? best.format() : "srt";
        return video.resolveSibling(stem + "." + best.language() + "." + ext);
    }

    /** Overridable for tests. */
    OpenSubtitlesClient createClient(Config config) {
        String key = config.get("opensubtitles.api.key");
        if (key == null) {
            throw new IllegalStateException(
                    "no OpenSubtitles API key set — set RENAMI_OPENSUBTITLES_API_KEY or opensubtitles.api.key in ~/.renami/config.properties");
        }
        return new OpenSubtitlesClient(key, new HttpJson(Config.dataDir().resolve("cache"), true));
    }
}
