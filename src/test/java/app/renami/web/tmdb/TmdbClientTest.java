package app.renami.web.tmdb;

import app.renami.web.JsonGet;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Request-shape tests for the TMDb client — the exact bug class that shipped in
 * ≤1.7.3 (a v3 key sent as a Bearer token, which TMDb rejects with 401).
 * No network, no real keys: the transport is stubbed and every request URL and
 * header set is asserted against the documented TMDb authentication spec.
 */
class TmdbClientTest {

    /** Records the last request and replays a canned answer or failure. */
    private static final class Stub implements JsonGet {
        private String url;
        private Map<String, String> headers;
        private final List<String> requested = new ArrayList<>();
        private String answer = "{\"results\":[]}";
        private IOException failure;

        @Override
        public JsonObject get(String url, Map<String, String> headers) throws IOException {
            this.url = url;
            this.headers = headers;
            requested.add(url);
            if (failure != null) {
                throw failure;
            }
            return JsonParser.parseString(answer).getAsJsonObject();
        }
    }

    /** A realistic-looking v3 key: 32 hex characters (the value TMDb shows first). */
    private static final String V3_KEY = "a1b2c3d4e5f60718293a4b5c6d7e8f90";

    /** A realistic-looking v4 read access token: a JWT (three dot-separated parts). */
    private static final String V4_TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                    + "eyJzdWIiOiJ1c2VyLXByb2ZpbGUtZGF0YS1mb3ItcmVhZC1hY2Nlc3MifQ."
                    + "ZGV2LXNpZ25hdHVyZS1zZWdtZW50LWxvbmdlci10aGFuLXNpeHR5LWNoYXJz";

    @Test
    void v3KeyIsDetectedAsApiKeyNotJwt() {
        assertFalse(TmdbClient.isReadAccessToken(V3_KEY));
        assertTrue(TmdbClient.isReadAccessToken(V4_TOKEN));
    }

    @Test
    void v3KeyGoesIntoTheUrlAsApiKeyParamAndNoHeader() throws Exception {
        Stub stub = new Stub();
        new TmdbClient(V3_KEY, stub).searchShow("Fargo", 0);
        assertTrue(stub.url.startsWith("https://api.themoviedb.org/3/search/tv?"), stub.url);
        assertTrue(stub.url.contains("query=Fargo"), stub.url);
        assertTrue(stub.url.contains("include_adult=false"), stub.url);
        assertTrue(stub.url.contains("api_key=" + V3_KEY),
                "v3 key must be sent as the api_key query parameter: " + stub.url);
        assertFalse(stub.url.contains("Bearer"), stub.url);
        assertTrue(stub.headers.isEmpty(), "v3 auth must not add an Authorization header: " + stub.headers);
    }

    @Test
    void v4TokenGoesIntoBearerHeaderAndNotTheUrl() throws Exception {
        Stub stub = new Stub();
        new TmdbClient(V4_TOKEN, stub).searchShow("Fargo", 0);
        assertEquals("Bearer " + V4_TOKEN, stub.headers.get("Authorization"));
        assertFalse(stub.url.contains("api_key"),
                "v4 read access token must not be echoed into the URL: " + stub.url);
    }

    @Test
    void whitespaceAroundAPastedKeyIsIgnored() throws Exception {
        Stub stub = new Stub();
        new TmdbClient("  " + V3_KEY + "\n", stub).searchShow("Fargo", 0);
        assertTrue(stub.url.contains("api_key=" + V3_KEY), stub.url);
    }

    @Test
    void languageParamAndApiKeyAreMergedWithAmpersand() throws Exception {
        Stub stub = new Stub();
        new TmdbClient(V3_KEY, stub, "de").getSeason("1399", 1);
        assertTrue(stub.url.startsWith("https://api.themoviedb.org/3/tv/1399/season/1?"), stub.url);
        assertTrue(stub.url.contains("language=de"), stub.url);
        assertTrue(stub.url.contains("&api_key=" + V3_KEY),
                "api_key must be appended after the language param, not replace it: " + stub.url);
    }

    @Test
    void yearIsOnlyAddedWhenKnown() throws Exception {
        Stub stub = new Stub();
        TmdbClient client = new TmdbClient(V3_KEY, stub);
        client.searchMovie("Die Hard", 0);
        assertFalse(stub.url.contains("&year="), stub.url);
        client.searchMovie("Die Hard", 1988);
        assertTrue(stub.url.contains("&year=1988"), stub.url);
    }

    @Test
    void seasonEpisodesAreParsed() throws Exception {
        Stub stub = new Stub();
        stub.answer = """
                {"episodes":[
                  {"season_number":1,"episode_number":1,"name":"Pilot","air_date":"2014-04-05"},
                  {"episode_number":2,"name":"Carter and Trudy","air_date":null}
                ]}""";
        var episodes = new TmdbClient(V3_KEY, stub).getSeason("1396", 1);
        assertEquals(2, episodes.size());
        assertEquals("Pilot", episodes.get(0).name());
        assertEquals(2, episodes.get(1).episode());
        assertEquals(1, episodes.get(1).season()); // missing season_number falls back to the requested season
        assertNull(episodes.get(1).airDate());
    }

    @Test
    void wrongV3KeyGetsGuidanceAboutWhichKeyToPaste() {
        Stub stub = new Stub();
        stub.failure = new IOException("authentication failed for https://api.themoviedb.org/3/search/tv"
                + " — check your API key");
        IOException e = assertThrows(IOException.class,
                () -> new TmdbClient("deadbeefdeadbeefdeadbeefdeadbeef", stub).searchShow("Fargo", 0));
        assertTrue(e.getMessage().contains("TMDb rejected this key"), e.getMessage());
        assertTrue(e.getMessage().contains("v3 api_key"), e.getMessage());
        assertTrue(e.getMessage().contains("API Key (v4 auth)"), e.getMessage());
    }

    @Test
    void wrongV4TokenGuidanceNamesTheBearerForm() {
        Stub stub = new Stub();
        stub.failure = new IOException("authentication failed for https://api.themoviedb.org/3/search/tv"
                + " — check your API key");
        IOException e = assertThrows(IOException.class,
                () -> new TmdbClient(V4_TOKEN, stub).searchShow("Fargo", 0));
        assertTrue(e.getMessage().contains("v4 read access token"), e.getMessage());
    }

    @Test
    void rateLimitMessagePassesThroughUnchanged() {
        Stub stub = new Stub();
        stub.failure = new IOException("rate limit reached for https://api.themoviedb.org/3/search/tv — slow down");
        IOException e = assertThrows(IOException.class,
                () -> new TmdbClient(V3_KEY, stub).searchShow("Fargo", 0));
        assertTrue(e.getMessage().startsWith("rate limit reached"), e.getMessage());
    }
}
