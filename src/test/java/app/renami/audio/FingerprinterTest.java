package app.renami.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real fpcalc round-trip; skipped unless FPCALC_TEST=on (requires chromaprint + ffmpeg). */
@EnabledIfEnvironmentVariable(named = "FPCALC_TEST", matches = "on")
class FingerprinterTest {

    @TempDir
    Path dir;

    @Test
    void fingerprintsGeneratedAudio() throws Exception {
        Path audio = dir.resolve("tone.mp3");
        Path ffmpeg = app.renami.util.NativeTools.resolve("ffmpeg");
        org.junit.jupiter.api.Assumptions.assumeTrue(ffmpeg != null, "ffmpeg not installed");
        new ProcessBuilder(ffmpeg.toString(), "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=5",
                audio.toString()).redirectErrorStream(true).start().waitFor();
        assertTrue(Files.isRegularFile(audio));

        assertTrue(Fingerprinter.isAvailable());
        Fingerprinter.Fingerprint fp = Fingerprinter.of(audio);
        assertEquals(5, (int) fp.duration());
        // a pure sine tone compresses to a very short fingerprint; just require non-empty
        assertTrue(!fp.fingerprint().isBlank());
    }
}
