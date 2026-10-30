package app.renami.util;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolution of native helper binaries (mediainfo, ffprobe, fpcalc, 7zz…).
 * PATH is tried first, then common Homebrew locations, so the app also works
 * when launched from Finder without a shell environment.
 */
public final class NativeTools {

    private static final String[] FALLBACK_DIRS = {"/opt/homebrew/bin", "/usr/local/bin"};

    private NativeTools() {
    }

    /** Absolute path to the binary, or null when it cannot be found. */
    public static Path resolve(String binary) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(":")) {
                if (dir.isBlank()) {
                    continue;
                }
                Path candidate = Path.of(dir, binary);
                if (Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        for (String dir : FALLBACK_DIRS) {
            Path candidate = Path.of(dir, binary);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean isAvailable(String binary) {
        return resolve(binary) != null;
    }
}
