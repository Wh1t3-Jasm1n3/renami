package app.renami.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SanitizeTest {

    @Test
    void collapsesEmptyFormatArtifacts() {
        assertEquals("Big Movie", RenameService.sanitize("Big Movie ()"));          // empty {y}
        assertEquals("Show - S01E02", RenameService.sanitize("Show - S01E02 - "));  // empty {t}
        assertEquals("untitled", RenameService.sanitize("()"));
    }

    @Test
    void replacesIllegalCharacters() {
        assertEquals("a-b", RenameService.sanitize("a/b"));
        assertEquals("clean", RenameService.sanitize("clean..."));
        assertEquals("no-control", RenameService.sanitize("no\tcontrol"));
    }
}
