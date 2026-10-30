package app.renami.web.tmdb;

import app.renami.web.Datasource;
import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Movie Database (TMDb) v3 API client.
 * Authentication: TMDb v4 *read access token* supplied by the user (never bundled).
 */
public final class TmdbClient implements Datasource {

    private static final String BASE = "https://api.themoviedb.org/3";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String accessToken;
    private final Map<String, List<EpisodeInfo>> seasonCache = new ConcurrentHashMap<>();

    public TmdbClient(String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public String name() {
        return "TMDb";
    }

    @Override
    public List<ShowInfo> searchShow(String query, int year) throws IOException {
        String url = BASE + "/search/tv?query=" + encode(query)
                + (year > 0 ? "&year=" + year : "") + "&include_adult=false";
        JsonObject json = get(url);
        List<ShowInfo> results = new ArrayList<>();
        for (JsonElement e : arr(json, "results")) {
            JsonObject o = e.getAsJsonObject();
            results.add(new ShowInfo(o.get("id").getAsInt(), str(o, "name"), str(o, "first_air_date")));
        }
        return results;
    }

    @Override
    public List<EpisodeInfo> getSeason(int showId, int season) throws IOException {
        String key = showId + "/" + season;
        List<EpisodeInfo> cached = seasonCache.get(key);
        if (cached != null) {
            return cached;
        }
        JsonObject json = get(BASE + "/tv/" + showId + "/season/" + season);
        List<EpisodeInfo> episodes = new ArrayList<>();
        for (JsonElement e : arr(json, "episodes")) {
            JsonObject o = e.getAsJsonObject();
            episodes.add(new EpisodeInfo(
                    o.has("season_number") ? o.get("season_number").getAsInt() : season,
                    o.get("episode_number").getAsInt(),
                    str(o, "name"),
                    str(o, "air_date")));
        }
        seasonCache.put(key, List.copyOf(episodes));
        return episodes;
    }

    @Override
    public List<MovieInfo> searchMovie(String query, int year) throws IOException {
        String url = BASE + "/search/movie?query=" + encode(query)
                + (year > 0 ? "&year=" + year : "") + "&include_adult=false";
        JsonObject json = get(url);
        List<MovieInfo> results = new ArrayList<>();
        for (JsonElement e : arr(json, "results")) {
            JsonObject o = e.getAsJsonObject();
            results.add(new MovieInfo(o.get("id").getAsInt(), str(o, "title"), str(o, "release_date")));
        }
        return results;
    }

    private JsonObject get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 401) {
                throw new IOException("TMDb authentication failed — check your read access token");
            }
            if (response.statusCode() == 429) {
                throw new IOException("TMDb rate limit reached — slow down");
            }
            if (response.statusCode() / 100 != 2) {
                throw new IOException("TMDb HTTP " + response.statusCode() + " for " + url);
            }
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling TMDb", e);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
