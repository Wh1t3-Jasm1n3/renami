package app.renami.web;

import app.renami.util.Config;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;
import app.renami.web.opensubtitles.OpenSubtitlesClient;

import java.util.List;

/**
 * The metadata services Renami knows about: where their keys come from, what they
 * cost a private user and how to prove a key works. This drives the API-key panel
 * (FileBot has the same list in its preferences) and the first-run setup dialog.
 * <p>
 * Terms verified November 2026: TMDb and OMDb are free for personal use, TVmaze and
 * Kitsu need no key at all, and TheTVDB only issues project keys that require either
 * a commercial licence or a paid subscriber PIN per user.
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
                        "free for personal use · movies and TV · the one key worth adding",
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
                new ApiService("tvdb", "TheTVDB", "tvdb.api.key", "tvdb.user.pin",
                        "https://www.thetvdb.com/dashboard/account/apikey",
                        "keys are project-only and need a paid subscriber PIN — optional",
                        true, PROBE_SHOW),
                new ApiService("opensubtitles", "OpenSubtitles", "opensubtitles.api.key", null,
                        "https://www.opensubtitles.com/en/consumers",
                        "free account · needed only for the Subtitles view",
                        true, PROBE_SUBTITLES),
                new ApiService("local", "Local (offline)", null, null, null,
                        "no key and no network · renames from filename facts",
                        false, null));
    }

    /**
     * Live check that a configured key works: one small lookup, answered with a
     * one-line summary the UI can show next to the field.
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
            List<OpenSubtitlesClient.Candidate> hits = new OpenSubtitlesClient(key,
                    new HttpJson(Config.dataDir().resolve("cache"), true)).search("Breaking Bad", "en");
            return hits.isEmpty() ? "connected, but no subtitles for the test query"
                    : "works · " + hits.size() + " subtitle hits, best: " + hits.get(0).title();
        }
        Datasource datasource = Datasources.create(config, service.id(), false);
        if (service.probeKind().equals(PROBE_SHOW)) {
            List<ShowInfo> hits = datasource.searchShow("Breaking Bad", 0);
            if (hits.isEmpty()) {
                throw new IllegalStateException("answered, but found no series for the test query");
            }
            return "works · " + hits.size() + " series hits, best: " + hits.get(0).name();
        }
        List<MovieInfo> movies = datasource.searchMovie("Die Hard", 1988);
        if (movies.isEmpty()) {
            List<ShowInfo> shows = datasource.searchShow("Breaking Bad", 0);
            if (shows.isEmpty()) {
                throw new IllegalStateException("answered, but found nothing for the test queries");
            }
            return "works · " + shows.size() + " series hits, best: " + shows.get(0).name();
        }
        return "works · " + movies.size() + " movie hits, best: " + movies.get(0).title();
    }
}
