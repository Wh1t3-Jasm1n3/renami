package app.renami.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The service table behind the API-key panel: honest terms, working links. */
class ApiServiceTest {

    @Test
    void everyFilebotServiceHasARow() {
        List<String> ids = ApiService.all().stream().map(ApiService::id).toList();
        assertEquals(List.of("tmdb", "tvmaze", "kitsu", "omdb", "tvdb", "opensubtitles", "local"), ids);
    }

    @Test
    void keyFreeServicesAreMarkedAsSuch() {
        for (ApiService service : ApiService.all()) {
            if (service.id().equals("tvmaze") || service.id().equals("kitsu") || service.id().equals("local")) {
                assertTrue(!service.needsKey(), service.id() + " needs no key");
                assertNull(service.keyProperty());
            } else {
                assertTrue(service.needsKey(), service.id() + " needs a key");
                assertTrue(service.keyProperty().endsWith(".api.key"));
            }
        }
    }

    @Test
    void signupLinksAreRealHttpsPages() {
        for (ApiService service : ApiService.all()) {
            if (service.signupUrl() != null) {
                assertTrue(service.signupUrl().startsWith("https://"), service.id());
            }
        }
    }

    @Test
    void tvdbCarriesItsSubscriberPinField() {
        ApiService tvdb = ApiService.all().stream().filter(s -> s.id().equals("tvdb")).findFirst().orElseThrow();
        assertEquals("tvdb.user.pin", tvdb.pinProperty());
        assertTrue(tvdb.terms().contains("PIN"));
    }

    @Test
    void localRowHasNoTestProbe() {
        ApiService local = ApiService.all().stream().filter(s -> s.id().equals("local")).findFirst().orElseThrow();
        assertNull(local.probeKind());
        assertNull(local.signupUrl());
    }
}
