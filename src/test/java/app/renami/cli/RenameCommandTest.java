package app.renami.cli;

import app.renami.rename.HistoryStore;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Full offline pipeline test: scan → parse → match → format → rename → undo. */
class RenameCommandTest {

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
    void renameThenUndoEndToEnd() throws Exception {
        Path f1 = Files.writeString(dir.resolve("breaking.bad.s01e01.1080p.webdl.mkv"), "a");
        Path f2 = Files.writeString(dir.resolve("breaking.bad.s01e02.1080p.webdl.mkv"), "b");

        Path historyFile = dir.resolve("history.jsonl");

        RenameCommand rename = new RenameCommand() {
            @Override
            Datasource createDatasource(Config config) {
                return new StubDatasource();
            }

            @Override
            HistoryStore createHistoryStore() {
                return new HistoryStore(historyFile);
            }
        };
        rename.input = dir;
        rename.apply = true;
        rename.mode = "auto";
        rename.conflict = app.renami.rename.ConflictPolicy.FAIL;
        rename.minScore = 0.6;

        assertEquals(0, rename.call());
        assertTrue(Files.exists(dir.resolve("Breaking Bad - S01E01 - Pilot.mkv")));
        assertTrue(Files.exists(dir.resolve("Breaking Bad - S01E02 - Cat in the Hat.mkv")));

        UndoCommand undo = new UndoCommand() {
            @Override
            HistoryStore createHistoryStore() {
                return new HistoryStore(historyFile);
            }
        };
        assertEquals(0, undo.call());
        assertTrue(Files.exists(f1));
        assertTrue(Files.exists(f2));
    }

    @Test
    void dryRunChangesNothing() throws Exception {
        Path f = Files.writeString(dir.resolve("breaking.bad.s01e02.mkv"), "x");

        RenameCommand rename = new RenameCommand() {
            @Override
            Datasource createDatasource(Config config) {
                return new StubDatasource();
            }
        };
        rename.input = dir;
        rename.apply = false;
        rename.mode = "auto";
        rename.conflict = app.renami.rename.ConflictPolicy.FAIL;
        rename.minScore = 0.6;

        assertEquals(0, rename.call());
        assertTrue(Files.exists(f));
        try (var s = Files.list(dir)) {
            assertEquals(1, s.count());
        }
    }

    @Test
    void missingApiKeyExitsWithCode2() throws Exception {
        Files.writeString(dir.resolve("some.show.1080p.mkv"), "x");
        RenameCommand rename = new RenameCommand() {
            @Override
            Datasource createDatasource(Config config) {
                throw new IllegalStateException("no TMDb API key");
            }
        };
        rename.input = dir;
        rename.mode = "auto";
        rename.conflict = app.renami.rename.ConflictPolicy.FAIL;
        rename.minScore = 0.6;
        assertEquals(2, rename.call());
    }

    @Test
    void localModeNeedsNoNetwork() throws Exception {
        Files.writeString(dir.resolve("breaking.bad.s01e02.1080p.webdl.mkv"), "x");

        RenameCommand rename = new RenameCommand() {
            @Override
            Datasource createDatasource(Config config) {
                return new app.renami.web.local.LocalDatasource();
            }

            @Override
            HistoryStore createHistoryStore() {
                return new HistoryStore(dir.resolve("history.jsonl"));
            }
        };
        rename.input = dir;
        rename.apply = true;
        rename.mode = "local";
        rename.conflict = app.renami.rename.ConflictPolicy.FAIL;
        rename.minScore = 0.6;

        assertEquals(0, rename.call());
        assertTrue(Files.exists(dir.resolve("breaking bad - S01E02.mkv")));
    }
}
