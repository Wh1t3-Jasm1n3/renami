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
        boolean queryCalled;
        boolean hashCalled;
        String lastQuery;

        StubClient() {
            super("stub", new HttpJson(null, false));
        }

        @Override
        public List<Candidate> search(String query, String language) {
            queryCalled = true;
            lastQuery = query;
            if (query.equalsIgnoreCase("Inception") || query.equalsIgnoreCase("Inception 2010")) {
                return List.of(
                        new Candidate(1, language, "Inception 2010 WEBRip", 42, "srt"),
                        new Candidate(2, language, "Inception 2010 BluRay", 108, "srt"));
            }
            return List.of();
        }

        @Override
        public List<Candidate> searchByHash(long moviehash, String language) {
            hashCalled = true;
            return List.of(new Candidate(99, language, "Inception 2010 EXACT HASH MATCH", 5, "srt"));
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

    @Test
    void hashMatchWinsForHashableFilesWithoutQuerySearch() throws Exception {
        byte[] data = new byte[70_000];
        Path video = dir.resolve("Inception.2010.1080p.mkv");
        Files.write(video, data);

        StubClient client = new StubClient();
        List<SubtitlesController.Row> rows = new SubtitlesController(client).preview(List.of(video), "en");

        assertEquals(1, rows.size());
        assertEquals(99, rows.get(0).candidate().id()); // exact hash match, despite fewer downloads
        assertTrue(client.hashCalled);
        assertEquals(false, client.queryCalled);
    }

    @Test
    void smallFilesFallBackToQuerySearch() throws Exception {
        Path video = Files.writeString(dir.resolve("Inception.2010.mkv"), "x");
        StubClient client = new StubClient();
        new SubtitlesController(client).preview(List.of(video), "en");
        assertEquals(false, client.hashCalled);
        assertTrue(client.queryCalled);
    }

    @Test
    void movieQueryCarriesTheYear() throws Exception {
        Path video = Files.writeString(dir.resolve("Inception.2010.mkv"), "x");
        StubClient client = new StubClient();
        new SubtitlesController(client).preview(List.of(video), "en");
        assertEquals("Inception 2010", client.lastQuery);
    }

    @Test
    void episodeQueryCarriesSeasonAndEpisode() {
        var info = app.renami.parse.FileNameParser.parse("Breaking.Bad.S01E02.1080p.mkv", "mkv");
        assertEquals("Breaking Bad S01E02", SubtitlesController.searchQuery(info));
    }

    @Test
    void closerTitleWinsOverMoreDownloads() {
        List<OpenSubtitlesClient.Candidate> candidates = List.of(
                new OpenSubtitlesClient.Candidate(1, "en", "Inception", 1, "srt"),
                new OpenSubtitlesClient.Candidate(2, "en", "Total Recall", 999, "srt"));
        assertEquals(1, SubtitlesController.bestByRelevance(candidates, "Inception").id());
    }
}
