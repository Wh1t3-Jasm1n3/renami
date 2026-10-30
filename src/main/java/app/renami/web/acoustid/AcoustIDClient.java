package app.renami.web.acoustid;

import app.renami.audio.Fingerprinter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AcouSTID lookup (the fingerprint database backed by MusicBrainz).
 * Authentication: user-supplied AcouSTID application key (free at acoustid.org).
 */
public final class AcoustIDClient {

    private static final String BASE = "https://api.acoustid.org/v2/lookup";

    public record Track(double score, String recordingId, String title, String artist) {
    }

    private final String clientKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public AcoustIDClient(String clientKey) {
        this.clientKey = clientKey;
    }

    public List<Track> lookup(Fingerprinter.Fingerprint fingerprint) throws IOException {
        String form = "client=" + URLEncoder.encode(clientKey, StandardCharsets.UTF_8)
                + "&format=json&meta=recordings"
                + "&duration=" + fingerprint.duration()
                + "&fingerprint=" + URLEncoder.encode(fingerprint.fingerprint(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " from AcouSTID");
            }
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    /** Exposed for offline testing against fixture JSON. */
    static List<Track> parse(String body) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        List<Track> tracks = new ArrayList<>();
        if (!json.has("results") || !json.get("results").isJsonArray()) {
            return tracks;
        }
        for (JsonElement e : json.getAsJsonArray("results")) {
            JsonObject result = e.getAsJsonObject();
            JsonObject recording = result.has("recording") ? result.getAsJsonObject("recording") : new JsonObject();
            String artist = "";
            if (recording.has("artists") && recording.get("artists").isJsonArray()) {
                JsonArray artists = recording.getAsJsonArray("artists");
                if (!artists.isEmpty()) {
                    artist = artists.get(0).getAsJsonObject().has("name")
                            ? artists.get(0).getAsJsonObject().get("name").getAsString() : "";
                }
            }
            tracks.add(new Track(
                    result.has("score") ? result.get("score").getAsDouble() : 0,
                    recording.has("id") ? recording.get("id").getAsString() : null,
                    recording.has("title") ? recording.get("title").getAsString() : "",
                    artist));
        }
        return tracks;
    }
}
