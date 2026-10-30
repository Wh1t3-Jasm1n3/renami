package app.renami.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Shells out to the MediaInfo CLI with JSON output. */
public final class MediaInfoCliProbe implements MediaProbe {

    @Override
    public String name() {
        return "mediainfo";
    }

    @Override
    public boolean isAvailable() {
        return app.renami.util.NativeTools.isAvailable("mediainfo");
    }

    @Override
    public Optional<JsonObject> probe(Path file) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(app.renami.util.NativeTools.resolve("mediainfo").toString(),
                "--Output=JSON", file.toAbsolutePath().toString())
                .redirectErrorStream(false)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("mediainfo timed out on " + file);
        }
        if (p.exitValue() != 0 || out.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(JsonParser.parseString(out).getAsJsonObject());
    }
}
