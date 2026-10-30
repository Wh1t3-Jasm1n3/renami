package app.renami.web.tvdb;

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
 * TheTVDB v4 API client. Authentication: user-supplied API key exchanged for a
 * short-lived JWT session token (kept in memory, never cached to disk).
 */
public final class TheTVDBClient implements Datasource {

    private static final String BASE = "https://api4.thetvdb.com/v0";

    private final HttpJson http;
    private final String apiKey;
    private String token;

    public TheTVDBClient(String apiKey, HttpJson http) {
        this.apiKey = apiKey;
        this.http = http;
    }

    @Override
    public String name() {
        return "TheTVDB";
    }

    private Map<String, String> auth() throws IOException {
        if (token == null) {
            JsonObject response = http.post(BASE + "/login",
                    "{\"apikey\": \"" + apiKey + "\"}", Map.of());
            if (!response.has("token")) {
                throw new IOException("TheTVDB login failed — check your API key");
            }
            token = response.get("token").getAsString();
        }
        return Map.of("Authorization", "Bearer " + token);
    }

    @Override
    public List<ShowInfo> searchShow(String query, int year) throws IOException {
        JsonObject json = http.get(BASE + "/search/series?name=" + URLEncoder.encode(query, StandardCharsets.UTF_8), auth());
        List<ShowInfo> results = new ArrayList<>();
        for (JsonElement e : arr(json, "data")) {
            JsonObject o = e.getAsJsonObject();
            results.add(new ShowInfo(str(o, "id"), str(o, "name"), str(o, "firstAired")));
        }
        return results;
    }

    @Override
    public List<EpisodeInfo> getSeason(String showId, int season) throws IOException {
        return getSeason(showId, season, "default");
    }

    @Override
    public List<EpisodeInfo> getSeason(String showId, int season, String order) throws IOException {
        boolean absolute = "absolute".equalsIgnoreCase(order);
        boolean allSeasons = order == null || order.equals("default");
        String url = absolute
                ? BASE + "/series/" + showId + "/episodes/absolute"
                : allSeasons
                ? BASE + "/series/" + showId + "/episodes/default"
                : BASE + "/series/" + showId + "/episodes/" + order + "/" + season;
        JsonObject json = http.get(url, auth());
        List<EpisodeInfo> episodes = new ArrayList<>();
        JsonObject data = json.has("data") && json.get("data").isJsonObject() ? json.getAsJsonObject("data") : new JsonObject();
        for (JsonElement e : arr(data, "episodes")) {
            JsonObject o = e.getAsJsonObject();
            int episodeNumber = parseNumber(str(o, "number"));
            if (episodeNumber <= 0 && o.has("airedEpisodeNumber") && !o.get("airedEpisodeNumber").isJsonNull()) {
                episodeNumber = o.get("airedEpisodeNumber").getAsInt();
            }
            if (episodeNumber <= 0) {
                continue; // specials and non-numeric entries
            }
            int seasonNumber = seasonOf(o, season);
            if (allSeasons && seasonNumber != season) {
                continue; // the default endpoint returns every season at once
            }
            episodes.add(new EpisodeInfo(seasonNumber, episodeNumber, str(o, "name"), str(o, "airDate")));
        }
        return episodes;
    }

    private static int seasonOf(JsonObject episode, int fallback) {
        if (episode.has("seasonNumber") && !episode.get("seasonNumber").isJsonNull()) {
            return episode.get("seasonNumber").getAsInt();
        }
        if (episode.has("airedSeason") && !episode.get("airedSeason").isJsonNull()) {
            return episode.get("airedSeason").getAsInt();
        }
        return fallback;
    }

    @Override
    public List<MovieInfo> searchMovie(String query, int year) throws IOException {
        return List.of(); // TV-focused datasource; movies are handled by TMDb/OMDb
    }

    /** TVDB episode numbers arrive as strings ("12", "12:1" for split episodes). */
    static int parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        String first = raw.split("[:.]")[0];
        try {
            int value = (int) Math.floor(Double.parseDouble(first));
            return value > 0 ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
