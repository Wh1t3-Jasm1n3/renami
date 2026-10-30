package app.renami.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * JSON HTTP helper with an on-disk response cache (~/.renami/cache, 24 h TTL).
 * Being polite to metadata providers: repeated lookups for the same show never hit the network.
 */
public final class HttpJson {

    private static final Duration TTL = Duration.ofHours(24);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Path cacheDir;
    private final boolean useCache;

    public HttpJson(Path cacheDir, boolean useCache) {
        this.cacheDir = cacheDir;
        this.useCache = useCache;
    }

    public JsonObject get(String url, Map<String, String> headers) throws IOException {
        Path entry = cacheEntry(url, headers);
        if (useCache && entry != null && Files.isRegularFile(entry)
                && Files.getLastModifiedTime(entry).toInstant().isAfter(Instant.now().minus(TTL))) {
            try {
                return JsonParser.parseString(Files.readString(entry)).getAsJsonObject();
            } catch (RuntimeException ignored) {
                // corrupt cache entry — refetch from network
            }
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET();
        headers.forEach(builder::header);
        String body = send(builder.build());
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (useCache && entry != null) {
            Files.createDirectories(entry.getParent());
            Files.writeString(entry, body);
        }
        return json;
    }

    public JsonObject post(String url, String jsonBody, Map<String, String> headers) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        return JsonParser.parseString(send(builder.build())).getAsJsonObject();
    }

    private String send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new IOException("authentication failed for " + request.uri() + " — check your API key");
            }
            if (status == 429) {
                throw new IOException("rate limit reached for " + request.uri() + " — slow down");
            }
            if (status / 100 != 2) {
                throw new IOException("HTTP " + status + " for " + request.uri());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while calling " + request.uri(), e);
        }
    }

    private Path cacheEntry(String url, Map<String, String> headers) {
        if (cacheDir == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(url.getBytes(StandardCharsets.UTF_8));
            headers.forEach((k, v) -> md.update((k + "=" + v + ";").getBytes(StandardCharsets.UTF_8)));
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) {
                hex.append(String.format("%02x", b));
            }
            return cacheDir.resolve(hex + ".json");
        } catch (Exception e) {
            return null;
        }
    }
}
