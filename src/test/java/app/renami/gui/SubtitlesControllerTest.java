package app.renami.gui;

import app.renami.web.HttpJson;
import app.renami.web.opensubtitles.OpenSubtitlesClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtitlesControllerTest {

    @TempDir
    Path dir;

    private static final class StubClient extends OpenSubtitlesClient {
        StubClient() {
            super("stub", new HttpJson(null, false));
        }

        @Override
        public List<Candidate> search(String query, String language) {
            if (query.equalsIgnoreCase("Inception")) {
                return List.of(
                        new Candidate(1, language, "Inception 2010 WEBRip", 42, "srt"),
                        new Candidate(2, language, "Inception 2010 BluRay", 108, "srt"));
            }
            return List.of();
        }

        @Override
        public Path download(long subtitleId, Path target) throws IOException {
            Files.writeString(target, "1\n00:00:00,000 --> 00:00:01,000\nstub " + subtitleId + "\n");
            return target;
        }
    }

    @Test
    void picksBestCandidateByDownloads() throws Exception {
        Path video = Files.writeString(dir.resolve("Inception.2010.1080p.mkv"), "x");
        SubtitlesController controller = new SubtitlesController(new StubClient());

        List<SubtitlesController.Row> rows = controller.preview(List.of(video), "en");
        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).candidate().id()); // 108 downloads wins
        assertEquals(dir.resolve("Inception.2010.1080p.en.srt"), rows.get(0).target());
    }

    @Test
    void downloadWritesSubtitleFile() throws Exception {
        Path video = Files.writeString(dir.resolve("Inception.2010.mkv"), "x");
        SubtitlesController controller = new SubtitlesController(new StubClient());
        SubtitlesController.Row row = controller.preview(List.of(video), "de").get(0);

        controller.download(row);
        assertTrue(Files.isRegularFile(dir.resolve("Inception.2010.de.srt")));
    }

    @Test
    void noMatchProducesNoRow() throws Exception {
        Path video = Files.writeString(dir.resolve("Unknown.Movie.mkv"), "x");
        assertEquals(0, new SubtitlesController(new StubClient()).preview(List.of(video), "en").size());
    }
}
