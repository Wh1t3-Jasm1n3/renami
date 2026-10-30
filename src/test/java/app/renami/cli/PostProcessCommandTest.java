package app.renami.cli;

import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline post-process e2e: loose file + zip → structured tree, archive cleaned. */
class PostProcessCommandTest {

    @TempDir
    Path dir;

    private static final class StubDatasource implements Datasource {
        @Override
        public String name() {
            return "stub";
        }

        @Override
        public List<ShowInfo> searchShow(String query, int year) {
            return List.of(new ShowInfo("1334", "Breaking Bad", "2008-01-20"));
        }

        @Override
        public List<EpisodeInfo> getSeason(String showId, int season) {
            return List.of(
                    new EpisodeInfo(1, 1, "Pilot", "2008-01-20"),
                    new EpisodeInfo(1, 2, "Cat in the Hat", "2008-01-27"));
        }

        @Override
        public List<MovieInfo> searchMovie(String query, int year) {
            return List.of();
        }
    }

    @Test
    void extractsMatchesMovesAndCleans() throws Exception {
        Path loose = dir.resolve("breaking.bad.s01e02.1080p.mkv");
        Files.writeString(loose, "loose");
        Path zip = dir.resolve("episode.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("breaking.bad.s01e01.720p.mkv"));
            out.write("from-zip".getBytes());
            out.closeEntry();
        }

        Path output = dir.resolve("tv");
        PostProcessCommand command = new PostProcessCommand() {
            @Override
            Datasource createDatasource(Config config) {
                return new StubDatasource();
            }

            @Override
            HistoryStore createHistoryStore() {
                return new HistoryStore(dir.resolve("history.jsonl"));
            }
        };
        command.input = dir;
        command.output = output;
        command.apply = true;
        command.clean = true;
        command.conflict = ConflictPolicy.INDEX;

        assertEquals(0, command.call());

        Path e1 = output.resolve("Breaking Bad/Season 1/Breaking Bad - S01E01 - Pilot.mkv");
        Path e2 = output.resolve("Breaking Bad/Season 1/Breaking Bad - S01E02 - Cat in the Hat.mkv");
        assertTrue(Files.isRegularFile(e1), "episode from zip moved into tree");
        assertTrue(Files.isRegularFile(e2), "loose episode moved into tree");
        assertEquals("from-zip", Files.readString(e1));
        assertTrue(Files.notExists(zip), "--clean removed the archive");
        assertTrue(Files.notExists(loose), "loose file was moved");
        assertTrue(Files.notExists(dir.resolve(".renami-extract")), "temp extraction dir cleaned");
    }

    @Test
    void companionSidecarsFollowIntoTheOutputTree() throws Exception {
        Files.writeString(dir.resolve("breaking.bad.s01e02.1080p.mkv"), "video");
        Files.writeString(dir.resolve("breaking.bad.s01e02.1080p.en.srt"), "subs");
        PostProcessCommand command = new PostProcessCommand() {
            @Override
            Datasource createDatasource(Config config) {
                return new StubDatasource();
            }

            @Override
            HistoryStore createHistoryStore() {
                return new HistoryStore(dir.resolve("history.jsonl"));
            }
        };
        command.input = dir;
        command.output = dir.resolve("tv");
        command.apply = true;
        command.conflict = ConflictPolicy.INDEX;

        assertEquals(0, command.call());

        Path sub = dir.resolve("tv/Breaking Bad/Season 1/Breaking Bad - S01E02 - Cat in the Hat.en.srt");
        assertTrue(Files.isRegularFile(sub), "subtitle renamed to match the episode, language kept");
        assertEquals("subs", Files.readString(sub));
        assertTrue(Files.notExists(dir.resolve("breaking.bad.s01e02.1080p.en.srt")), "sidecar moved, not copied");
    }

    @Test
    void companionFalseLeavesSidecarsBehind() throws Exception {
        Files.writeString(dir.resolve("breaking.bad.s01e02.1080p.mkv"), "video");
        Files.writeString(dir.resolve("breaking.bad.s01e02.1080p.en.srt"), "subs");
        PostProcessCommand command = new PostProcessCommand() {
            @Override
            Datasource createDatasource(Config config) {
                return new StubDatasource();
            }

            @Override
            HistoryStore createHistoryStore() {
                return new HistoryStore(dir.resolve("history.jsonl"));
            }
        };
        command.input = dir;
        command.output = dir.resolve("tv");
        command.apply = true;
        command.companion = false;
        command.conflict = ConflictPolicy.INDEX;

        assertEquals(0, command.call());
        assertTrue(Files.exists(dir.resolve("breaking.bad.s01e02.1080p.en.srt")), "--no-companion leaves the sidecar");
    }

    @Test
    void dryRunLeavesEverythingInPlace() throws Exception {
        Files.writeString(dir.resolve("breaking.bad.s01e02.mkv"), "x");
        PostProcessCommand command = new PostProcessCommand() {
            @Override
            Datasource createDatasource(Config config) {
                return new StubDatasource();
            }
        };
        command.input = dir;
        command.output = dir.resolve("tv");
        command.apply = false;
        command.conflict = ConflictPolicy.INDEX;

        assertEquals(0, command.call());
        assertTrue(Files.exists(dir.resolve("breaking.bad.s01e02.mkv")));
        assertTrue(Files.notExists(dir.resolve("tv")));
    }
}
