package app.renami.web.tmdb;

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
 * The Movie Database (TMDb) v3 API client.
 * Authentication: TMDb v4 *read access token* supplied by the user (never bundled).
 */
public final class TmdbClient implements Datasource {

    private static final String BASE = "https://api.themoviedb.org/3";

    private final HttpJson http;
    private final Map<String, String> auth;
    private final String language;

    public TmdbClient(String accessToken, HttpJson http) {
        this(accessToken, http, null);
    }

    /** @param language optional series-language (e.g. "de") for localized episode names */
    public TmdbClient(String accessToken, HttpJson http, String language) {
        this.http = http;
        this.auth = Map.of("Authorization", "Bearer " + accessToken);
        this.language = language;
    }

    @Override
    public String name() {
        return "TMDb";
    }

    @Override
    public List<ShowInfo> searchShow(String query, int year) throws IOException {
        String url = BASE + "/search/tv?query=" + encode(query)
                + (year > 0 ? "&year=" + year : "") + "&include_adult=false";
        JsonObject json = http.get(url, auth);
        List<ShowInfo> results = new ArrayList<>();
        for (JsonElement e : arr(json, "results")) {
            JsonObject o = e.getAsJsonObject();
            results.add(new ShowInfo(str(o, "id"), str(o, "name"), str(o, "first_air_date")));
        }
        return results;
    }

    @Override
    public List<EpisodeInfo> getSeason(String showId, int season) throws IOException {
        return getSeason(showId, season, "default", null);
    }

    @Override
    public List<EpisodeInfo> getSeason(String showId, int season, String order, String language) throws IOException {
        String lang = language != null ? language : this.language;
        String url = BASE + "/tv/" + showId + "/season/" + season
                + (lang != null && !lang.isBlank() ? "?language=" + encode(lang) : "");
        JsonObject json = http.get(url, auth);
        List<EpisodeInfo> episodes = new ArrayList<>();
        for (JsonElement e : arr(json, "episodes")) {
            JsonObject o = e.getAsJsonObject();
            episodes.add(new EpisodeInfo(
                    o.has("season_number") ? o.get("season_number").getAsInt() : season,
                    o.get("episode_number").getAsInt(),
                    str(o, "name"),
                    str(o, "air_date")));
        }
        return episodes;
    }

    @Override
    public List<MovieInfo> searchMovie(String query, int year) throws IOException {
        String url = BASE + "/search/movie?query=" + encode(query)
                + (year > 0 ? "&year=" + year : "") + "&include_adult=false";
        JsonObject json = http.get(url, auth);
        List<MovieInfo> results = new ArrayList<>();
        for (JsonElement e : arr(json, "results")) {
            JsonObject o = e.getAsJsonObject();
            results.add(new MovieInfo(str(o, "id"), str(o, "title"), str(o, "release_date")));
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
