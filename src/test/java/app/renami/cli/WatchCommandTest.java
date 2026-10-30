package app.renami.cli;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchCommandTest {

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
            return List.of(new EpisodeInfo(1, 1, "Pilot", "2008-01-20"));
        }

        @Override
        public List<MovieInfo> searchMovie(String query, int year) {
            return List.of();
        }
    }

    @Test
    void invalidActionExitsWithUsageError() throws Exception {
        WatchCommand command = new WatchCommand();
        command.input = dir;
        command.db = "local";
        command.action = "bogus";
        assertEquals(2, command.call());
    }

    @Test
    void watchRenamesNewFilesThroughTheFullPipeline() throws Exception {
        Path history = dir.resolve("history.jsonl");
        WatchCommand command = new WatchCommand() {
            @Override
            Datasource createDatasource(Config config) {
                return new StubDatasource();
            }

            @Override
            HistoryStore createHistoryStore() {
                return new HistoryStore(history);
            }
        };
        command.input = dir;
        command.apply = true;
        command.settleSeconds = 1;

        Thread runner = new Thread(() -> {
            try {
                command.call();
            } catch (Exception ignored) {
                // watcher interrupted by stop()
            }
        });
        runner.setDaemon(true);
        runner.start();
        Thread.sleep(400); // let the watch loop come up

        Files.writeString(dir.resolve("breaking.bad.s01e01.1080p.mkv"), "x");

        Path renamed = dir.resolve("Breaking Bad - S01E01 - Pilot.mkv");
        long deadline = System.currentTimeMillis() + 15_000;
        while (!Files.exists(renamed) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        command.stop();
        runner.join(5_000);

        assertTrue(Files.exists(renamed), "watched file renamed by the pipeline");
        assertTrue(Files.exists(history), "watched batch journalled for undo");
    }
}
