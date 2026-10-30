package app.renami.web.kitsu;

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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Kitsu (kitsu.io) — Renami's anime datasource, standing in for AniDB: AniDB's
 * legacy HTTP API (anime_query.php) has been retired and its replacement needs a
 * registered, cryptographically signed client, while Kitsu needs no key at all
 * and carries AniDB-derived anime and episode data.
 * <p>
 * JSON:API shape: {@code {"data":[{"id":"1","attributes":{…}}],"links":{"next":…}}}.
 * Kitsu caps page size at 20, so episode lists are walked with offset pagination.
 * JSON:API parameter names are percent-encoded because '[' and ']' are not valid
 * in a {@link java.net.URI} query.
 */
public class KitsuClient implements Datasource {

    static final String BASE = "https://kitsu.io/api/edge";
    static final int PAGE_SIZE = 20;
    static final int MAX_PAGES = 30; // 600 episodes per record: enough for any series

    private static final String TEXT_FILTER = "filter%5Btext%5D";
    private static final String SUBTYPE_FILTER = "filter%5Bsubtype%5D";
    private static final String NUMBER_FILTER = "filter%5Bnumber%5D";
    private static final String PAGE_LIMIT = "page%5Blimit%5D";
    private static final String PAGE_OFFSET = "page%5Boffset%5D";

    /** Kitsu is a JSON:API server: it answers 406 unless asked for the profile media type. */
    private static final Map<String, String> JSONAPI = Map.of("Accept", "application/vnd.api+json");

    /** An episode as Kitsu stores it: in-record number plus the (rare) absolute number. */
    record EpisodeRecord(int season, int number, int absolute, String name, String airDate) {

        EpisodeInfo toInfo() {
            return new EpisodeInfo(season, number > 0 ? number : absolute, name, airDate);
        }
    }

    private final JsonGet http;

    public KitsuClient(JsonGet http) {
        this.http = http;
    }

    @Override
    public String name() {
        return "Kitsu";
    }

    @Override
    public List<ShowInfo> searchShow(String query, int year) throws IOException {
        JsonObject json = http.get(BASE + "/anime?" + TEXT_FILTER + "=" + encode(query)
                + "&" + PAGE_LIMIT + "=" + PAGE_SIZE, JSONAPI);
        List<ShowInfo> results = new ArrayList<>();
        for (JsonElement e : arr(json, "data")) {
            JsonObject record = e.getAsJsonObject();
            JsonObject attributes = attributes(record);
            String id = str(record, "id");
            String startDate = str(attributes, "startDate");
            if (id == null) {
                continue;
            }
            // anime are released under several names (romaji, english, native, abbreviations):
            // every one of them becomes its own candidate so the scorer can match any of them
            for (String title : titles(attributes)) {
                results.add(new ShowInfo(id, title, startDate));
            }
        }
        return results;
    }

    /**
     * All names Kitsu knows for one anime, in preference order and without duplicates:
     * canonicalTitle, the {@code titles} map (en_jp is the romaji/original romanised name,
     * ja_jp the native script title) and {@code abbreviatedTitles}.
     */
    static List<String> titles(JsonObject attributes) {
        List<String> names = new ArrayList<>();
        addName(names, str(attributes, "canonicalTitle"));
        JsonObject titles = attributes.has("titles") && attributes.get("titles").isJsonObject()
                ? attributes.getAsJsonObject("titles") : new JsonObject();
        for (String key : new String[]{"en_jp", "en", "en_us", "ja_jp"}) {
            addName(names, str(titles, key));
        }
        for (JsonElement alias : arr(attributes, "abbreviatedTitles")) {
            if (alias.isJsonNull()) {
                continue;
            }
            addName(names, alias.getAsString());
        }
        return names;
    }

    private static void addName(List<String> names, String candidate) {
        if (candidate == null) {
            return;
        }
        String name = candidate.trim();
        if (!name.isEmpty() && !names.contains(name)) {
            names.add(name);
        }
    }

    @Override
    public List<EpisodeInfo> getSeason(String showId, int season) throws IOException {
        List<EpisodeRecord> all = episodes(showId);
        List<EpisodeRecord> matching = all.stream().filter(r -> r.season() == season).toList();
        // Kitsu models each season as its own record, so a record that carries no
        // episode for the requested season is used as-is rather than answered empty.
        return (matching.isEmpty() ? all : matching).stream().map(EpisodeRecord::toInfo).toList();
    }

    @Override
    public List<EpisodeInfo> getSeason(String showId, int season, String order) throws IOException {
        if (!"absolute".equalsIgnoreCase(order)) {
            return getSeason(showId, season);
        }
        return episodes(showId).stream()
                .sorted(Comparator.comparingInt(r -> r.absolute() > 0 ? r.absolute() : r.number()))
                .map(EpisodeRecord::toInfo)
                .toList();
    }

    /**
     * A Kitsu record is normally one season/cour, so its in-record number already is
     * the absolute one: ask for that number directly (one request) before scanning
     * the whole record for an explicit {@code numberAbsolute}.
     */
    @Override
    public EpisodeInfo mapAbsoluteEpisode(String showId, int absolute) throws IOException {
        JsonObject json = http.get(BASE + "/anime/" + showId + "/episodes?" + NUMBER_FILTER + "="
                + absolute + "&" + PAGE_LIMIT + "=1", JSONAPI);
        for (JsonElement e : arr(json, "data")) {
            EpisodeRecord record = episode(attributes(e.getAsJsonObject()), 1);
            if (record != null) {
                return record.toInfo();
            }
        }
        for (EpisodeRecord record : episodes(showId)) {
            if (record.absolute() == absolute) {
                return record.toInfo();
            }
        }
        return null;
    }

    @Override
    public List<MovieInfo> searchMovie(String query, int year) throws IOException {
        JsonObject json = http.get(BASE + "/anime?" + TEXT_FILTER + "=" + encode(query)
                + "&" + SUBTYPE_FILTER + "=movie&" + PAGE_LIMIT + "=" + PAGE_SIZE, JSONAPI);
        List<MovieInfo> results = new ArrayList<>();
        for (JsonElement e : arr(json, "data")) {
            JsonObject record = e.getAsJsonObject();
            JsonObject attributes = attributes(record);
            String id = str(record, "id");
            if (id == null) {
                continue;
            }
            String startDate = str(attributes, "startDate");
            for (String title : titles(attributes)) {
                results.add(new MovieInfo(id, title, startDate));
            }
        }
        return results;
    }

    /** All episodes of one anime record, following Kitsu's offset pagination. */
    List<EpisodeRecord> episodes(String showId) throws IOException {
        List<EpisodeRecord> records = new ArrayList<>();
        String url = BASE + "/anime/" + showId + "/episodes?" + PAGE_LIMIT + "=" + PAGE_SIZE
                + "&" + PAGE_OFFSET + "=0";
        for (int page = 0; page < MAX_PAGES && url != null; page++) {
            JsonObject json = http.get(url, JSONAPI);
            for (JsonElement e : arr(json, "data")) {
                EpisodeRecord record = episode(attributes(e.getAsJsonObject()), 1);
                if (record != null) {
                    records.add(record);
                }
            }
            url = nextLink(json);
        }
        return records;
    }

    private static EpisodeRecord episode(JsonObject attributes, int fallbackSeason) {
        int number = num(attributes, "number");
        int absolute = num(attributes, "numberAbsolute");
        if (number <= 0 && absolute <= 0) {
            return null; // specials and unnumbered entries
        }
        int season = num(attributes, "seasonNumber");
        String name = str(attributes, "canonicalTitle");
        if (name == null) {
            JsonObject titles = attributes.has("titles") && attributes.get("titles").isJsonObject()
                    ? attributes.getAsJsonObject("titles") : new JsonObject();
            name = str(titles, "en_jp");
        }
        return new EpisodeRecord(season > 0 ? season : fallbackSeason, number, absolute, name,
                str(attributes, "airdate"));
    }

    /** @return the JSON:API cursor for the next page, or null when this was the last one. */
    static String nextLink(JsonObject json) {
        if (!json.has("links") || !json.get("links").isJsonObject()) {
            return null;
        }
        JsonObject links = json.getAsJsonObject("links");
        if (!links.has("next") || links.get("next").isJsonNull()) {
            return null;
        }
        String next = links.get("next").getAsString();
        return next.isBlank() ? null : next;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static JsonObject attributes(JsonElement record) {
        JsonObject object = record.getAsJsonObject();
        return object.has("attributes") && object.get("attributes").isJsonObject()
                ? object.getAsJsonObject("attributes") : new JsonObject();
    }

    private static JsonArray arr(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
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
}
