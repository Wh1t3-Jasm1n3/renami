package app.renami.web.xem;

import app.renami.web.JsonGet;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * TheXEM (XEMingForest) client: community-maintained anime episode mappings.
 * Used as a fallback when a show's TVDB absolute order is incomplete — maps an
 * absolute episode number to the corresponding TVDB episode id. No API key needed.
 */
public final class XemClient {

    private static final String BASE = "https://thexem.de/xem";

    private final JsonGet http;
    private final Map<String, Map<Integer, Long>> mappingsBySeries = new HashMap<>();

    public XemClient(JsonGet http) {
        this.http = http;
    }

    /** TVDB episode id for an absolute anime episode number, when XEM knows the mapping. */
    public Optional<Long> tvdbEpisodeId(String tvdbSeriesId, int absolute) throws IOException {
        Map<Integer, Long> mapping = mappingsBySeries.get(tvdbSeriesId);
        if (mapping == null) {
            JsonObject response = http.get(BASE + "/getMappedTVDB/" + tvdbSeriesId, Map.of());
            mapping = parseMapping(response);
            mappingsBySeries.put(tvdbSeriesId, mapping);
        }
        return Optional.ofNullable(mapping.get(absolute));
    }

    /** XEM response: {"status":"Success","data":{"<absolute>":{"id":<tvdb episode id>, …}}} */
    static Map<Integer, Long> parseMapping(JsonObject response) {
        Map<Integer, Long> mapping = new HashMap<>();
        if (!response.has("data") || !response.get("data").isJsonObject()) {
            return mapping;
        }
        for (Map.Entry<String, JsonObject> entry : entryObjects(response.getAsJsonObject("data"))) {
            JsonObject value = entry.getValue();
            long episodeId = value.has("id") && !value.get("id").isJsonNull()
                    ? value.get("id").getAsLong() : -1;
            int absolute = value.has("absolute") && !value.get("absolute").isJsonNull()
                    ? value.get("absolute").getAsInt() : Integer.parseInt(entry.getKey());
            if (episodeId > 0 && absolute > 0) {
                mapping.put(absolute, episodeId);
            }
        }
        return mapping;
    }

    private static Iterable<Map.Entry<String, JsonObject>> entryObjects(JsonObject object) {
        Map<String, JsonObject> entries = new HashMap<>();
        object.entrySet().forEach(e -> {
            if (e.getValue().isJsonObject()) {
                entries.put(e.getKey(), e.getValue().getAsJsonObject());
            }
        });
        return entries.entrySet();
    }
}
