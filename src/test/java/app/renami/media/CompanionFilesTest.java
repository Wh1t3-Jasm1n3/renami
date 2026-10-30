package app.renami.media;

import app.renami.rename.RenameOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionFilesTest {

    @TempDir
    java.nio.file.Path dir;

    private java.nio.file.Path touch(String name) throws Exception {
        return java.nio.file.Files.writeString(dir.resolve(name), "x");
    }

    @Test
    void findsSidecarsSharingTheBaseName() throws Exception {
        var movie = touch("Movie.mkv");
        var sub = touch("Movie.srt");
        var langSub = touch("Movie.en.srt");
        var threeLetterSub = touch("Movie.eng.sub");
        var nfo = touch("Movie.nfo");
        var art = touch("Movie.jpg");

        List<java.nio.file.Path> companions = CompanionFiles.find(movie);
        assertEquals(List.of(langSub, threeLetterSub, art, nfo, sub), companions);
    }

    @Test
    void ignoresUnrelatedHiddenPartialAndMediaSiblings() throws Exception {
        var movie = touch("Movie.mkv");
        touch("Other.srt");          // different base name
        touch(".Movie.srt");         // hidden
        touch("Movie.part");         // partial download
        touch("Movie.tmp");          // temp
        touch("Movie.mp4");          // media with same base: planned on its own
        touch("Movie.zip");          // archive

        assertEquals(List.of(), CompanionFiles.find(movie));
    }

    @Test
    void expandDragsCompanionsToTheMediaTarget() throws Exception {
        var movie = touch("breaking.bad.s01e01.1080p.mkv");
        var sub = touch("breaking.bad.s01e01.1080p.srt");
        var langSub = touch("breaking.bad.s01e01.1080p.en.srt");

        var out = dir.resolve("tv/Breaking Bad - S01E01 - Pilot.mkv");
        List<RenameOperation> expanded = CompanionFiles.expand(
                List.of(new RenameOperation(movie, out)));

        assertEquals(3, expanded.size());
        assertEquals(out, expanded.get(0).target());
        assertEquals(dir.resolve("tv/Breaking Bad - S01E01 - Pilot.en.srt"),
                expanded.stream().filter(r -> r.source().equals(langSub)).findFirst().orElseThrow().target());
        assertTrue(expanded.stream().anyMatch(r ->
                r.source().equals(sub)
                        && r.target().getFileName().toString().equals("Breaking Bad - S01E01 - Pilot.srt")));
    }

    @Test
    void expandNeverDuplicatesFilesAlreadyInThePlan() throws Exception {
        var movie = touch("Movie.mkv");
        var sub = touch("Movie.srt");
        // the subtitle is also a planned media file (e.g. second video's sidecar scenario)
        List<RenameOperation> expanded = CompanionFiles.expand(List.of(
                new RenameOperation(movie, dir.resolve("New Movie.mkv")),
                new RenameOperation(sub, dir.resolve("New Movie.srt"))));
        assertEquals(2, expanded.size());
    }
}
