package app.renami.web.omdb;

import app.renami.web.JsonGet;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Request-shape tests for OMDb: the key travels as the {@code apikey} query
 * parameter and nothing goes into headers. Error mapping is asserted with a
 * deliberately wrong key — free to test, no quota involved.
 */
class OmdbClientTest {

    private static final class Stub implements JsonGet {
        private String url;
        private Map<String, String> headers;
        private String answer = "{\"Search\":[]}";
        private IOException failure;

        @Override
        public JsonObject get(String url, Map<String, String> headers) throws IOException {
            this.url = url;
            this.headers = headers;
            if (failure != null) {
                throw failure;
            }
            return JsonParser.parseString(answer).getAsJsonObject();
        }
    }

    @Test
    void movieSearchPutsKeyInUrlAndEncodesQuery() throws Exception {
        Stub stub = new Stub();
        new OmdbClient("abc12345", stub).searchMovie("Die Hard", 1988);
        assertEquals("https://www.omdbapi.com/?apikey=abc12345&s=Die+Hard&type=movie&y=1988", stub.url);
        assertTrue(stub.headers.isEmpty(), "OMDb auth is the query parameter only: " + stub.headers);
    }

    @Test
    void seriesSearchAsksForSeriesType() throws Exception {
        Stub stub = new Stub();
        new OmdbClient("abc12345", stub).searchShow("Breaking Bad", 0);
        assertTrue(stub.url.contains("type=series"), stub.url);
        assertTrue(stub.url.contains("apikey=abc12345"), stub.url);
    }

    @Test
    void seasonLookupUsesImdbIdAndSeasonParam() throws Exception {
        Stub stub = new Stub();
        stub.answer = """
                {"Response":"True","Season":"2","Episodes":[
                  {"Episode":"1","title":"Seven Thirty-Seven","released":"2009-03-08"},
                  {"Episode":"2","title":"Grilled Cheesus","released":"2009-03-15"}
                ]}""";
        var episodes = new OmdbClient("abc12345", stub).getSeason("tt0903747", 2);
        assertTrue(stub.url.contains("i=tt0903747"), stub.url);
        assertTrue(stub.url.contains("Season=2"), stub.url);
        assertEquals(2, episodes.size());
        assertEquals("Seven Thirty-Seven", episodes.get(0).name());
        assertEquals(1, episodes.get(0).episode());
        assertEquals(2, episodes.get(1).episode());
    }

    @Test
    void badKeySurfacesTheAuthenticationFailure() {
        Stub stub = new Stub();
        stub.failure = new IOException("authentication failed for https://www.omdbapi.com/ — check your API key");
        IOException e = assertThrows(IOException.class,
                () -> new OmdbClient("wrong-key", stub).searchMovie("Die Hard", 1988));
        assertTrue(e.getMessage().contains("check your API key"), e.getMessage());
    }

    @Test
    void rateLimitSurfacesAsRateLimit() {
        Stub stub = new Stub();
        stub.failure = new IOException("rate limit reached for https://www.omdbapi.com/ — slow down");
        IOException e = assertThrows(IOException.class,
                () -> new OmdbClient("abc12345", stub).searchMovie("Die Hard", 1988));
        assertTrue(e.getMessage().contains("rate limit"), e.getMessage());
    }
}
