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
public class TheTVDBClient implements Datasource {

    private static final String BASE = "https://api4.thetvdb.com/v0";

    private final HttpJson http;
    private final String apiKey;
    private String token;
    app.renami.web.xem.XemClient xem; // package-visible: tests substitute a stub

    public TheTVDBClient(String apiKey, HttpJson http) {
        this.apiKey = apiKey;
        this.http = http;
        this.xem = new app.renami.web.xem.XemClient(http);
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
        return getSeason(showId, season, order, null);
    }

    @Override
    public List<EpisodeInfo> getSeason(String showId, int season, String order, String language) throws IOException {
        boolean absolute = "absolute".equalsIgnoreCase(order);
        boolean allSeasons = order == null || order.equals("default");
        String url = absolute
                ? BASE + "/series/" + showId + "/episodes/absolute"
                : allSeasons
                ? BASE + "/series/" + showId + "/episodes/default"
                : BASE + "/series/" + showId + "/episodes/" + order + "/" + season;
        if (language != null && !language.isBlank()) {
            url += "?lang=" + language;
        }
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

    /**
     * Absolute-number resolution with a TheXEM fallback: TVDB's absolute order is
     * community-curated and often incomplete for anime, so a miss is retried against
     * the XEM mapping (absolute → TVDB episode id → episode details).
     */
    @Override
    public EpisodeInfo mapAbsoluteEpisode(String showId, int absolute) throws IOException {
        EpisodeInfo direct = Datasource.super.mapAbsoluteEpisode(showId, absolute);
        if (direct != null || !showId.matches("\\d+")) {
            return direct;
        }
        java.util.Optional<Long> episodeId = xem.tvdbEpisodeId(showId, absolute);
        return episodeId.isEmpty() ? null : fetchEpisode(episodeId.get());
    }

    /** Single TVDB episode by id (v4 /episodes/{id}). */
    EpisodeInfo fetchEpisode(long tvdbEpisodeId) throws IOException {
        JsonObject json = http.get(BASE + "/episodes/" + tvdbEpisodeId, auth());
        JsonObject data = json.has("data") && json.get("data").isJsonObject()
                ? json.getAsJsonObject("data") : new JsonObject();
        int season = data.has("airedSeason") && !data.get("airedSeason").isJsonNull()
                ? data.get("airedSeason").getAsInt() : 1;
        int episode = parseNumber(str(data, "airedEpisode"));
        if (episode <= 0) {
            episode = parseNumber(str(data, "number"));
        }
        if (episode <= 0) {
            return null;
        }
        return new EpisodeInfo(season, episode, str(data, "name"), str(data, "firstAired"));
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
