package app.renami.media;

import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.Optional;

/** Technical metadata probe for a media file. */
public interface MediaProbe {

    String name();

    boolean isAvailable();

    /** @return raw provider JSON (MediaInfo/FFprobe track structure) */
    Optional<JsonObject> probe(Path file) throws Exception;
}
