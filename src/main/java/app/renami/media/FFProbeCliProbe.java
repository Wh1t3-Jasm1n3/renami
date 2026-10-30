package app.renami.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Fallback probe using ffprobe JSON output. */
public final class FFProbeCliProbe implements MediaProbe {

    @Override
    public String name() {
        return "ffprobe";
    }

    @Override
    public boolean isAvailable() {
        return app.renami.util.NativeTools.isAvailable("ffprobe");
    }

    @Override
    public Optional<JsonObject> probe(Path file) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(
                app.renami.util.NativeTools.resolve("ffprobe").toString(), "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams",
                file.toAbsolutePath().toString())
                .redirectErrorStream(false)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("ffprobe timed out on " + file);
        }
        if (out.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(JsonParser.parseString(out).getAsJsonObject());
    }
}
