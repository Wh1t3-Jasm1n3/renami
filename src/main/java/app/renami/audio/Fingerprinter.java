package app.renami.audio;

import app.renami.util.NativeTools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Audio fingerprinting via the native fpcalc helper (chromaprint). */
public final class Fingerprinter {

    public record Fingerprint(double duration, String fingerprint) {
    }

    private Fingerprinter() {
    }

    public static boolean isAvailable() {
        return NativeTools.isAvailable("fpcalc");
    }

    public static Fingerprint of(Path audio) throws IOException {
        Path fpcalc = NativeTools.resolve("fpcalc");
        if (fpcalc == null) {
            throw new IOException("fpcalc not found — install it with `brew install chromaprint`");
        }
        Process process = new ProcessBuilder(fpcalc.toString(), "-json", audio.toAbsolutePath().toString())
                .redirectErrorStream(false)
                .start();
        String out = new String(process.getInputStream().readAllBytes());
        int code;
        try {
            code = process.waitFor(120, TimeUnit.SECONDS) ? process.exitValue() : -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        if (code != 0) {
            throw new IOException("fpcalc failed (exit " + code + ") for " + audio.getFileName());
        }
        JsonObject json = JsonParser.parseString(out).getAsJsonObject();
        return new Fingerprint(json.get("duration").getAsDouble(), json.get("fingerprint").getAsString());
    }
}
