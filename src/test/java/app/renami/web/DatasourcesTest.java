package app.renami.web;

import app.renami.util.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Datasource selection: tvmaze resolves, and auto never demands a key. */
class DatasourcesTest {

    @Test
    void tvmazeNeedsNoConfiguration() {
        Datasource datasource = Datasources.create(Config.load(), "tvmaze", true);
        assertEquals("TVmaze", datasource.name());
    }

    @Test
    void autoAlwaysBuildsAndEndsWithTheKeyFreeProviders() {
        // Whatever keys this machine happens to have, the chain must build and
        // must finish with the providers that answer without any configuration.
        FallbackDatasource chain = assertInstanceOf(FallbackDatasource.class,
                Datasources.create(Config.load(), "auto", true));
        assertEquals(java.util.List.of("TVmaze", "Kitsu"),
                chain.providers().subList(chain.providers().size() - 2, chain.providers().size()));
    }

    @Test
    void unknownDatasourceNamesListTheRealOptions() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> Datasources.create(Config.load(), "anidb", true));
        assertTrue(error.getMessage().contains("tvmaze"));
    }

    @Test
    void removedTvdbProviderPointsAtTheCurrentOptions() {
        // Users whose old config or script still says --db tvdb get a helpful error, not a stack trace.
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> Datasources.create(Config.load(), "tvdb", true));
        assertTrue(error.getMessage().contains("unknown datasource"), error.getMessage());
        assertTrue(error.getMessage().contains("tmdb"), error.getMessage());
    }

    @Test
    void keyFreeProvidersAreAdvertised() {
        assertEquals(java.util.List.of("tvmaze", "kitsu", "local"), Datasources.keyFree());
    }
}
