package app.renami.tui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathTaskViewTest {

    private static final Path FIXTURE =
            Path.of("/private/var/folders/8z/n9ttwq8559l6y4myttpkymr40000gn/T/opencode/journey/TV_FIXTURE");

    @Test
    void listViewListsMediaFiles() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(FIXTURE));
        List<String> lines = new ListView().compute(FIXTURE);
        assertEquals(1, lines.size()); // the .srt is a companion, not a media file
        assertTrue(lines.stream().anyMatch(l -> l.endsWith(".mkv")));
    }

    @Test
    void checksumViewHashesMediaFiles() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(FIXTURE));
        List<String> lines = new ChecksumView().compute(FIXTURE);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).matches("^[0-9a-f]{64}  .+$"));
    }

    @Test
    void enterRunsTheWorkerAndPublishesResults() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(FIXTURE));
        ListView view = new ListView();
        for (char c : FIXTURE.toString().toCharArray()) {
            view.onKey(new com.googlecode.lanterna.input.KeyStroke(c, false, false));
        }
        view.onKey(new com.googlecode.lanterna.input.KeyStroke(
                com.googlecode.lanterna.input.KeyType.Enter));
        long deadline = System.currentTimeMillis() + 10_000;
        while (view.busy() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(view.busy(), "worker did not finish");
        assertTrue(view.status().startsWith("1 results"), "status was: " + view.status());
    }
}
