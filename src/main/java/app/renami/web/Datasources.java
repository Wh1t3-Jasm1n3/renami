package app.renami.web;

import app.renami.util.Config;
import app.renami.web.kitsu.KitsuClient;
import app.renami.web.local.LocalDatasource;
import app.renami.web.omdb.OmdbClient;
import app.renami.web.tmdb.TmdbClient;
import app.renami.web.tvdb.TheTVDBClient;

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
            case "kitsu" -> new KitsuClient(http);
            case "local" -> new LocalDatasource();
            case "auto" -> {
                if (config.get("tmdb.api.key") != null) yield tmdb(config, http);
                if (config.get("tvdb.api.key") != null) yield tvdb(config, http);
                if (config.get("omdb.api.key") != null) yield omdb(config, http);
                throw new IllegalStateException(
                        "no datasource API key configured (RENAMI_TMDB_API_KEY / RENAMI_TVDB_API_KEY / RENAMI_OMDB_API_KEY) — "
                                + "or use the key-free --db kitsu (anime) / --db local (offline)");
            }
            default -> throw new IllegalStateException(
                    "unknown datasource: " + db + " (use auto|tmdb|tvdb|omdb|kitsu|local)");
        };
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
        return new TheTVDBClient(key, http);
    }

    private static Datasource omdb(Config config, HttpJson http) {
        String key = config.get("omdb.api.key");
        if (key == null) {
            throw new IllegalStateException("OMDb selected but no key set — set RENAMI_OMDB_API_KEY or omdb.api.key in ~/.renami/config.properties");
        }
        return new OmdbClient(key, http);
    }
}
