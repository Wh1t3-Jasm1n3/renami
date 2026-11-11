package app.renami.web.tvmaze;

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
 * TVmaze (api.tvmaze.com) — Renami's key-free TV datasource, the reason the app
 * works on first launch with no configuration at all.
 * <p>
 * TVmaze answers with bare JSON arrays (hence {@link JsonGet#getTree}), is rate
 * limited to ~20 requests per 10 s per IP, and asks for a descriptive User-Agent.
 * Its public API covers shows and episodes only: there is no movie search
 * endpoint, so {@link #searchMovie} stays empty and movies keep needing TMDb/OMDb.
 * <p>
 * Data is CC BY-SA, so the app credits TVmaze wherever a lookup is shown.
 */
public class TvmazeClient implements Datasource {

    static final String BASE = "https://api.tvmaze.com";

    /** TVmaze asks clients to identify themselves (see the API rate-limit notes). */
    private static final Map<String, String> HEADERS =
            Map.of("User-Agent", "Renami (open-source media renamer; https://github.com/Wh1t3-Jasm1n3/renami)");

    private final JsonGet http;

    public TvmazeClient(JsonGet http) {
        this.http = http;
    }

    @Override
    public String name() {
        return "TVmaze";
    }

    /** {@code /search/shows} answers {@code [{"score":…,"show":{…}}]}. */
    @Override
    public List<ShowInfo> searchShow(String query, int year) throws IOException {
        JsonElement root = http.getTree(BASE + "/search/shows?q=" + encode(query), HEADERS);
        List<ShowInfo> results = new ArrayList<>();
        for (JsonElement hit : array(root)) {
            JsonObject show = hit.isJsonObject() && hit.getAsJsonObject().has("show")
                    ? hit.getAsJsonObject().getAsJsonObject("show")
                    : hit.getAsJsonObject();
            String id = str(show, "id");
            String title = str(show, "name");
            if (id == null || title == null) {
                continue;
            }
            results.add(new ShowInfo(id, title, firstAired(show)));
        }
        return results;
    }

    /**
     * One request per show: {@code /shows/{id}/episodes} returns the whole aired list
     * in broadcast order, so the season is filtered locally instead of paging per season.
     */
    @Override
    public List<EpisodeInfo> getSeason(String showId, int season) throws IOException {
        JsonElement root = http.getTree(BASE + "/shows/" + showId + "/episodes", HEADERS);
        List<EpisodeInfo> episodes = new ArrayList<>();
        for (JsonElement e : array(root)) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject episode = e.getAsJsonObject();
            int number = num(episode, "number");
            if (number <= 0) {
                continue; // unnumbered entries cannot drive a rename
            }
            int airedSeason = num(episode, "season");
            if (airedSeason != season) {
                continue;
            }
            episodes.add(new EpisodeInfo(season, number, str(episode, "name"), str(episode, "airdate")));
        }
        return episodes;
    }

    /**
     * TVmaze has no public movie endpoint (its {@code /search/movie} route 404s),
     * so movie lookups deliberately return nothing and the caller falls through to
     * the next datasource in the chain.
     */
    @Override
    public List<MovieInfo> searchMovie(String query, int year) {
        return List.of();
    }

    /** Shows carry their premiere date as {@code premiered}; older payloads used {@code firstAired}. */
    static String firstAired(JsonObject show) {
        String premiered = str(show, "premiered");
        return premiered != null ? premiered : str(show, "firstAired");
    }

    private static JsonArray array(JsonElement root) {
        return root != null && root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
    }

    private static String str(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private static int num(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return -1;
        }
        try {
            return (int) Math.floor(object.get(key).getAsDouble());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
