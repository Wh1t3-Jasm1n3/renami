package app.renami.web.tmdb;

import app.renami.web.Datasource;
import app.renami.web.JsonGet;
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
 * <p>
 * TMDb hands out two different credentials on the same settings page and they are
 * not interchangeable: the 32-character <i>API Key (v3 auth)</i> must be sent as the
 * {@code api_key} query parameter, while the long <i>API Key (v4 auth)</i> read access
 * token (a JWT) must be sent as a {@code Bearer} header. Both are accepted here and
 * detected from the value itself, so pasting either one just works.
 */
public final class TmdbClient implements Datasource {

    private static final String BASE = "https://api.themoviedb.org/3";

    private final JsonGet http;
    private final String credential;
    private final boolean bearerAuth;
    private final Map<String, String> auth;
    private final String language;

    public TmdbClient(String accessToken, JsonGet http) {
        this(accessToken, http, null);
    }

    /** @param language optional series-language (e.g. "de") for localized episode names */
    public TmdbClient(String accessToken, JsonGet http, String language) {
        this.http = http;
        this.credential = accessToken == null ? "" : accessToken.replaceAll("\\s+", "");
        this.bearerAuth = isReadAccessToken(this.credential);
        this.auth = bearerAuth ? Map.of("Authorization", "Bearer " + this.credential) : Map.of();
        this.language = language;
    }

    /** A v4 read access token is a JWT (three dot-separated parts); a v3 key is a plain hex string. */
    static boolean isReadAccessToken(String value) {
        long dots = value.chars().filter(c -> c == '.').count();
        return dots == 2 && value.length() > 60;
    }

    /** Builds a request URL, adding the credential in whichever form it is. */
    String url(String path, String query) {
        StringBuilder url = new StringBuilder(BASE).append(path);
        char sep = '?';
        if (query != null && !query.isBlank()) {
            url.append('?').append(query);
            sep = '&';
        }
        if (!bearerAuth) {
            url.append(sep).append("api_key=").append(encode(credential));
        }
        return url.toString();
    }

    private JsonObject get(String url) throws IOException {
        try {
            return http.get(url, auth);
        } catch (IOException failure) {
            String message = failure.getMessage();
            if (message != null && message.contains("authentication failed")) {
                throw new IOException("TMDb rejected this key (sent as "
                        + (bearerAuth ? "a v4 read access token" : "a v3 api_key")
                        + ") — copy the whole value from themoviedb.org ▸ Settings ▸ API: "
                        + "\"API Key (v3 auth)\" or \"API Key (v4 auth)\".", failure);
            }
            throw failure;
        }
    }

    @Override
    public String name() {
        return "TMDb";
    }

    @Override
    public List<ShowInfo> searchShow(String query, int year) throws IOException {
        String q = "query=" + encode(query) + (year > 0 ? "&year=" + year : "") + "&include_adult=false";
        JsonObject json = get(url("/search/tv", q));
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
        String q = lang != null && !lang.isBlank() ? "language=" + encode(lang) : null;
        JsonObject json = get(url("/tv/" + showId + "/season/" + season, q));
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
        String q = "query=" + encode(query) + (year > 0 ? "&year=" + year : "") + "&include_adult=false";
        JsonObject json = get(url("/search/movie", q));
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
