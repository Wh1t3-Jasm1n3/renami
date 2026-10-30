package app.renami.cli;

import app.renami.util.Config;
import app.renami.web.opensubtitles.OpenSubtitlesClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubtitlesCommandTest {

    @TempDir
    Path dir;

    @Test
    void subtitleFileNaming() {
        Path video = Path.of("/tv/Breaking Bad - S01E02.mkv");
        var candidate = new OpenSubtitlesClient.Candidate(1, "en", "Some Release", 42, "srt");
        assertEquals("Breaking Bad - S01E02.en.srt", SubtitlesCommand.subtitlePath(video, candidate).getFileName().toString());

        var unknownFormat = new OpenSubtitlesClient.Candidate(1, "de", "x", 1, null);
        assertEquals("Breaking Bad - S01E02.de.srt", SubtitlesCommand.subtitlePath(video, unknownFormat).getFileName().toString());
    }

    @Test
    void missingApiKeyExitsWithCode2() throws Exception {
        Files.writeString(dir.resolve("some.show.mkv"), "x");
        SubtitlesCommand command = new SubtitlesCommand() {
            @Override
            OpenSubtitlesClient createClient(Config config) {
                throw new IllegalStateException("no OpenSubtitles API key");
            }
        };
        command.input = dir;
        assertEquals(2, command.call());
    }
}
