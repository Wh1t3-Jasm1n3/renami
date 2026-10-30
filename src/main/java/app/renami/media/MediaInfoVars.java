package app.renami.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps raw MediaInfo / ffprobe JSON onto the flat format variables used by
 * templates and the list command (vf, vres, ac, duration, size).
 */
public final class MediaInfoVars {

    private MediaInfoVars() {
    }

    public static Map<String, Object> extract(JsonObject json) {
        return json.has("media") ? fromMediaInfo(json) : fromFfprobe(json);
    }

    private static Map<String, Object> fromMediaInfo(JsonObject json) {
        Map<String, Object> vars = new LinkedHashMap<>();
        JsonElement media = json.get("media");
        if (!(media instanceof JsonObject mediaObj) || !mediaObj.has("track")) {
            return vars;
        }
        for (JsonElement element : mediaObj.getAsJsonArray("track")) {
            if (!(element instanceof JsonObject track)) {
                continue;
            }
            String type = str(track, "@type");
            switch (type == null ? "" : type) {
                case "General" -> {
                    putIfPresent(vars, "size", parseLong(str(track, "FileSize")));
                    putIfPresent(vars, "duration", parseLong(str(track, "Duration")));
                }
                case "Video" -> {
                    vars.put("vf", str(track, "Format"));
                    String width = str(track, "Width");
                    String height = str(track, "Height");
                    if (width != null && height != null) {
                        vars.put("vres", width + "x" + height);
                    }
                    putIfPresent(vars, "bitdepth", parseLong(str(track, "BitDepth")));
                }
                case "Audio" -> vars.putIfAbsent("ac", str(track, "Format"));
            }
        }
        return vars;
    }

    private static Map<String, Object> fromFfprobe(JsonObject json) {
        Map<String, Object> vars = new LinkedHashMap<>();
        if (json.has("format") && json.get("format").isJsonObject()) {
            JsonObject format = json.getAsJsonObject("format");
            putIfPresent(vars, "size", parseLong(str(format, "size")));
            Double duration = parseDouble(str(format, "duration"));
            if (duration != null) {
                vars.put("duration", (long) (double) duration);
            }
        }
        if (json.has("streams") && json.get("streams").isJsonArray()) {
            JsonArray streams = json.getAsJsonArray("streams");
            for (JsonElement element : streams) {
                if (!(element instanceof JsonObject stream)) {
                    continue;
                }
                String type = str(stream, "codec_type");
                if ("video".equals(type) && !vars.containsKey("vf")) {
                    vars.put("vf", str(stream, "codec_name"));
                    Integer width = parseInt(str(stream, "width"));
                    Integer height = parseInt(str(stream, "height"));
                    if (width != null && height != null) {
                        vars.put("vres", width + "x" + height);
                    }
                } else if ("audio".equals(type) && !vars.containsKey("ac")) {
                    vars.put("ac", str(stream, "codec_name"));
                }
            }
        }
        return vars;
    }

    private static void putIfPresent(Map<String, Object> vars, String key, Object value) {
        if (value != null) {
            vars.put(key, value);
        }
    }

    private static String str(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private static Long parseLong(String s) {
        try {
            return s == null ? null : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String s) {
        try {
            return s == null ? null : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String s) {
        try {
            return s == null ? null : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
