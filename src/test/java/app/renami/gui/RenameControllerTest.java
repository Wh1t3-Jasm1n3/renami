package app.renami.gui;

import app.renami.pipeline.RenameService;
import app.renami.rename.ConflictPolicy;
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

class RenameControllerTest {

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
            return List.of(new EpisodeInfo(1, 2, "Cat in the Hat", "2008-01-27"));
        }

        @Override
        public List<MovieInfo> searchMovie(String query, int year) {
            return List.of();
        }
    }

    @Test
    void previewThenApplyProducesRenamedRows() throws Exception {
        Path file = Files.writeString(dir.resolve("breaking.bad.s01e02.mkv"), "x");
        RenameController controller = new RenameController(new StubDatasource());

        List<RenameController.Row> preview = controller.preview(
                List.of(file), new RenameService.Options("auto", null, 0.6));
        assertEquals(1, preview.size());
        assertEquals("rename", preview.get(0).status());
        assertEquals("Breaking Bad - S01E02 - Cat in the Hat.mkv", preview.get(0).target().getFileName().toString());

        List<RenameController.Row> applied = controller.apply(preview, ConflictPolicy.FAIL);
        assertEquals("renamed", applied.get(0).status());
        assertTrue(Files.exists(preview.get(0).target()));
    }

    @Test
    void unmatchedFilesShownAsNoMatch() throws Exception {
        Path file = Files.writeString(dir.resolve("totally.unknown.xyz.1080p.mkv"), "x");
        RenameController controller = new RenameController(new Datasource() {
            @Override
            public String name() {
                return "empty";
            }

            @Override
            public List<ShowInfo> searchShow(String query, int year) {
                return List.of();
            }

            @Override
            public List<EpisodeInfo> getSeason(String showId, int season) {
                return List.of();
            }

            @Override
            public List<MovieInfo> searchMovie(String query, int year) {
                return List.of();
            }
        });
        List<RenameController.Row> preview = controller.preview(
                List.of(file), new RenameService.Options("auto", null, 0.6));
        assertEquals(List.of("no match"), preview.stream().map(RenameController.Row::status).toList());
    }
}
