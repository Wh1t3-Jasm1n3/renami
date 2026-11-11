package app.renami.web;

import app.renami.util.Config;
import app.renami.web.kitsu.KitsuClient;
import app.renami.web.local.LocalDatasource;
import app.renami.web.omdb.OmdbClient;
import app.renami.web.tmdb.TmdbClient;
import app.renami.web.tvdb.TheTVDBClient;
import app.renami.web.tvmaze.TvmazeClient;

import java.util.ArrayList;
import java.util.List;

/** Datasource selection from the --db option and configured API keys. */
public final class Datasources {

    private Datasources() {
    }

    /** @throws IllegalStateException when the requested datasource has no API key */
    public static Datasource create(Config config, String db, boolean noCache) {
        HttpJson http = new HttpJson(Config.dataDir().resolve("cache"), !noCache);
        return switch (db.toLowerCase()) {
            case "tmdb" -> tmdb(config, http);
            case "tvdb" -> tvdb(config, http);
            case "omdb" -> omdb(config, http);
            case "tvmaze" -> new TvmazeClient(http);
            case "kitsu" -> new KitsuClient(http);
            case "local" -> new LocalDatasource();
            case "auto" -> autoChain(config, http);
            default -> throw new IllegalStateException(
                    "unknown datasource: " + db + " (use auto|tmdb|tvdb|tvmaze|omdb|kitsu|local)");
        };
    }

    /**
     * The {@code auto} chain: keyed providers the user actually configured first
     * (best coverage), then the key-free ones so a fresh install still resolves
     * TV shows without any setup. Never fails for a missing key.
     */
    static Datasource autoChain(Config config, HttpJson http) {
        List<Datasource> chain = new ArrayList<>();
        if (config.get("tmdb.api.key") != null) {
            chain.add(tmdb(config, http));
        }
        if (config.get("tvdb.api.key") != null) {
            chain.add(tvdb(config, http));
        }
        if (config.get("omdb.api.key") != null) {
            chain.add(omdb(config, http));
        }
        chain.add(new TvmazeClient(http)); // TV/episodes, no key
        chain.add(new KitsuClient(http));  // anime, no key
        return new FallbackDatasource(chain);
    }

    /** Providers that work without any API key, for the setup UI and the FAQ. */
    public static List<String> keyFree() {
        return List.of("tvmaze", "kitsu", "local");
    }

    private static Datasource tmdb(Config config, HttpJson http) {
        String key = config.get("tmdb.api.key");
        if (key == null) {
            throw new IllegalStateException("TMDb selected but no key set — set RENAMI_TMDB_API_KEY or tmdb.api.key in ~/.renami/config.properties");
        }
        return new TmdbClient(key, http, config.get("datasource.language"));
    }

    private static Datasource tvdb(Config config, HttpJson http) {
        String key = config.get("tvdb.api.key");
        if (key == null) {
            throw new IllegalStateException("TheTVDB selected but no key set — set RENAMI_TVDB_API_KEY or tvdb.api.key in ~/.renami/config.properties");
        }
        return new TheTVDBClient(key, config.get("tvdb.user.pin"), http);
    }

    private static Datasource omdb(Config config, HttpJson http) {
        String key = config.get("omdb.api.key");
        if (key == null) {
            throw new IllegalStateException("OMDb selected but no key set — set RENAMI_OMDB_API_KEY or omdb.api.key in ~/.renami/config.properties");
        }
        return new OmdbClient(key, http);
    }
}
