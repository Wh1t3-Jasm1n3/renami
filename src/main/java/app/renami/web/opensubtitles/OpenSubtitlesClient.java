package app.renami.web.opensubtitles;

import app.renami.web.JsonGet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenSubtitles.com REST API v2 client.
 * Authentication: user-supplied API key (free tier at opensubtitles.com) plus
 * account login when private subs are needed; anonymous search works with just the key.
 */
public class OpenSubtitlesClient { // non-final: tests substitute a stub transport

    private static final String BASE = "https://api.opensubtitles.com/api/v1";

    public record Candidate(long id, String language, String title, int downloads, String format) {
    }

    private final JsonGet http;
    private final Map<String, String> auth;
    private String token;

    public OpenSubtitlesClient(String apiKey, JsonGet http) {
        this.http = http;
        this.auth = Map.of("Apikey", apiKey);
    }

    public List<Candidate> search(String query, String language) throws IOException {
        String url = BASE + "/subtitles?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&languages=" + URLEncoder.encode(language, StandardCharsets.UTF_8)
                + "&sort=download_count";
        return parseCandidates(http.get(url, auth()));
    }

    /** Exact-match search by the 64-bit moviehash (legacy endpoint, same response shape). */
    public List<Candidate> searchByHash(long moviehash, String language) throws IOException {
        String url = BASE + "/subtitles/search/legacy?moviehash=" + Long.toHexString(moviehash)
                + "&languages=" + URLEncoder.encode(language, StandardCharsets.UTF_8);
        return parseCandidates(http.get(url, auth()));
    }

    private List<Candidate> parseCandidates(JsonObject json) {
        List<Candidate> results = new ArrayList<>();
        if (json.has("data") && json.get("data").isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray("data")) {
                JsonObject o = e.getAsJsonObject();
                JsonObject attrs = o.has("attributes") ? o.getAsJsonObject("attributes") : new JsonObject();
                results.add(new Candidate(
                        o.get("id").getAsLong(),
                        str(attrs, "language"),
                        str(attrs, "title"),
                        attrs.has("download_count") ? attrs.get("download_count").getAsInt() : 0,
                        str(attrs, "format")));
            }
        }
        return results;
    }

    /** Fetch the download link, then the subtitle file itself (binary-safe, not JSON-cached). */
    public Path download(long subtitleId, Path target) throws IOException {
        JsonObject json = http.post(BASE + "/download", "{\"file_id\": " + subtitleId + "}", auth());
        String link = json.has("link") ? json.get("link").getAsString() : null;
        if (link == null) {
            throw new IOException("OpenSubtitles returned no download link for " + subtitleId);
        }
        try {
            HttpClient raw = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<byte[]> response = raw.send(
                    HttpRequest.newBuilder(URI.create(link)).header("User-Agent", UA).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " downloading subtitle");
            }
            Files.createDirectories(target.getParent());
            Files.write(target, response.body());
            return target;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private static final String UA = "Renami https://github.com/Wh1t3-Jasm1n3/renami";

    private Map<String, String> auth() throws IOException {
        if (token != null) {
            return Map.of("Apikey", auth.get("Apikey"), "Authorization", "Bearer " + token);
        }
        return auth;
    }

    /** Optional account login; anonymous search works without it. */
    public void login(String username, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        JsonObject response = http.post(BASE + "/login", body.toString(), auth);
        if (response.has("token")) {
            token = response.get("token").getAsString();
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
