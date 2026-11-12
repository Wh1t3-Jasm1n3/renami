package app.renami.web;

import app.renami.util.Config;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;
import app.renami.web.opensubtitles.OpenSubtitlesClient;

import java.util.List;

/**
 * The metadata services Renami knows about: where their keys come from, what they
 * cost a private user and how to prove a key works. This drives the single setup
 * sheet (Help ▸ Set Up Renami…).
 * <p>
 * Terms verified November 2026: TMDb and OMDb are free for personal use, and TVmaze
 * and Kitsu need no key at all. TheTVDB was removed in 1.7.4: it only issues project
 * keys to commercial apps or paywall users with a subscriber PIN, so no private user
 * (and never us) could verify the integration end-to-end.
 */
public record ApiService(String id,
                         String label,
                         String keyProperty,
                         String pinProperty,
                         String signupUrl,
                         String terms,
                         boolean needsKey,
                         String probeKind) {

    /** Probe kinds: what a "Test" press asks the provider for. */
    public static final String PROBE_MOVIE = "movie";
    public static final String PROBE_SHOW = "show";
    public static final String PROBE_SUBTITLES = "subtitles";

    public static List<ApiService> all() {
        return List.of(
                new ApiService("tmdb", "TMDb", "tmdb.api.key", null,
                        "https://www.themoviedb.org/settings/api",
                        "free · movies and TV",
                        true, PROBE_MOVIE),
                new ApiService("tvmaze", "TVmaze", null, null,
                        "https://www.tvmaze.com/api",
                        "no key needed · TV shows and episodes only",
                        false, PROBE_SHOW),
                new ApiService("kitsu", "Kitsu", null, null,
                        "https://kitsu.io",
                        "no key needed · anime and episode lists",
                        false, PROBE_SHOW),
                new ApiService("omdb", "OMDb", "omdb.api.key", null,
                        "https://www.omdbapi.com/apikey.aspx",
                        "free · 1 000 lookups a day · IMDb data",
                        true, PROBE_MOVIE),
                new ApiService("opensubtitles", "OpenSubtitles", "opensubtitles.api.key", null,
                        "https://www.opensubtitles.com/en/consumers",
                        "free account · Subtitles view only",
                        true, PROBE_SUBTITLES),
                new ApiService("local", "Local (offline)", null, null, null,
                        "no key and no network · renames from filename facts",
                        false, null));
    }

    /**
     * Live check that a configured key works: one small lookup, answered with a
     * one-line summary the UI can show next to the field. The lookup bypasses
     * the 24 h response cache — a cached answer would not prove the typed key works.
     *
     * @throws Exception whatever the provider reports (bad key, no network, rate limit)
     */
    public static String probe(ApiService service) throws Exception {
        Config config = Config.load();
        if (service.probeKind().equals(PROBE_SUBTITLES)) {
            String key = config.get(service.keyProperty());
            if (key == null) {
                throw new IllegalStateException("no key configured");
            }
            new OpenSubtitlesClient(key,
                    new HttpJson(Config.dataDir().resolve("cache"), false)).search("Breaking Bad", "en");
            return "Key works";
        }
        Datasource datasource = Datasources.create(config, service.id(), true);
        if (service.probeKind().equals(PROBE_SHOW)) {
            if (datasource.searchShow("Breaking Bad", 0).isEmpty()) {
                throw new IllegalStateException("answered, but found no series for the test query");
            }
            return "Connected";
        }
        if (!datasource.searchMovie("Die Hard", 1988).isEmpty()) {
            return "Key works";
        }
        if (datasource.searchShow("Breaking Bad", 0).isEmpty()) {
            throw new IllegalStateException("answered, but found nothing for the test queries");
        }
        return "Key works";
    }
}
