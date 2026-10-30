package app.renami.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Recursive media file discovery based on file extensions. */
public final class MediaScanner {

    public static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mkv", "mp4", "m4v", "avi", "mov", "wmv", "flv", "webm", "ts", "m2ts", "mpg", "mpeg", "ogv");

    public static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "alac", "aiff", "ape");

    private MediaScanner() {
    }

    public static List<Path> scan(Path root) throws IOException {
        if (Files.isRegularFile(root)) {
            return isMedia(root) ? List.of(root) : List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> result = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .filter(MediaScanner::isMedia)
                    .sorted()
                    .forEach(result::add);
            return result;
        }
    }

    public static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isVideo(Path path) {
        return VIDEO_EXTENSIONS.contains(extension(path));
    }

    public static boolean isAudio(Path path) {
        return AUDIO_EXTENSIONS.contains(extension(path));
    }

    public static boolean isMedia(Path path) {
        return isVideo(path) || isAudio(path);
    }
}
