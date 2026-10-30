package app.renami.web.omdb;

import app.renami.web.Datasource;
import app.renami.web.HttpJson;
import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OMDb (Open Movie Database) client. Authentication: user-supplied API key as query parameter.
 * Episodes come from the season endpoint; episode numbers are positional (OMDb returns them in order).
 */
public final class OmdbClient implements Datasource {

    private static final String BASE = "https://www.omdbapi.com";

    private final HttpJson http;
    private final String apiKey;

    public OmdbClient(String apiKey, HttpJson http) {
        this.apiKey = apiKey;
        this.http = http;
    }

    @Override
    public String name() {
        return "OMDb";
    }

    @Override
    public List<ShowInfo> searchShow(String query, int year) throws IOException {
        String url = BASE + "/?apikey=" + apiKey + "&s=" + encode(query) + "&type=series";
        JsonObject json = http.get(url, Map.of());
        List<ShowInfo> results = new ArrayList<>();
        for (JsonElement e : arr(json, "Search")) {
            JsonObject o = e.getAsJsonObject();
            results.add(new ShowInfo(str(o, "imdbID"), str(o, "Title"), str(o, "Year")));
        }
        return results;
    }

    @Override
    public List<EpisodeInfo> getSeason(String showId, int season) throws IOException {
        String url = BASE + "/?apikey=" + apiKey + "&i=" + encode(showId) + "&Season=" + season;
        JsonObject json = http.get(url, Map.of());
        List<EpisodeInfo> episodes = new ArrayList<>();
        int position = 1;
        for (JsonElement e : arr(json, "Episodes")) {
            JsonObject o = e.getAsJsonObject();
            episodes.add(new EpisodeInfo(season, position++, str(o, "title"), str(o, "released")));
        }
        return episodes;
    }

    @Override
    public List<MovieInfo> searchMovie(String query, int year) throws IOException {
        String url = BASE + "/?apikey=" + apiKey + "&s=" + encode(query) + "&type=movie"
                + (year > 0 ? "&y=" + year : "");
        JsonObject json = http.get(url, Map.of());
        List<MovieInfo> results = new ArrayList<>();
        for (JsonElement e : arr(json, "Search")) {
            JsonObject o = e.getAsJsonObject();
            results.add(new MovieInfo(str(o, "imdbID"), str(o, "Title"), str(o, "Year")));
        }
        return results;
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
