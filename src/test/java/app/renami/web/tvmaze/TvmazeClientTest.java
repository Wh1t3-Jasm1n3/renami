package app.renami.web.tvmaze;

import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.ShowInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TVmaze parsing against recorded payloads — no network in tests. */
class TvmazeClientTest {

    /** Serves fixtures by URL pattern, records requests and their headers. */
    private static final class Stub implements app.renami.web.JsonGet {
        private final Map<String, String> byUrl;
        private final List<String> requested = new ArrayList<>();
        private final List<Map<String, String>> headers = new ArrayList<>();

        Stub(Map<String, String> byUrl) {
            this.byUrl = byUrl;
        }

        @Override
        public JsonElement getTree(String url, Map<String, String> hdrs) throws IOException {
            requested.add(url);
            headers.add(hdrs);
            for (Map.Entry<String, String> entry : byUrl.entrySet()) {
                if (url.contains(entry.getKey())) {
                    return JsonParser.parseString(entry.getValue());
                }
            }
            throw new IOException("unexpected request: " + url);
        }

        @Override
        public com.google.gson.JsonObject get(String url, Map<String, String> hdrs) {
            throw new UnsupportedOperationException("TVmaze answers arrays only");
        }
    }

    private static final String SEARCH = """
            [
              {"score":1.18,"show":{"id":169,"name":"Breaking Bad","premiered":"2008-01-20"}},
              {"score":0.5,"show":{"id":61292,"name":"Breaking Bad (2019)"}},
              {"score":0.2,"show":{"id":1,"name":null}}
            ]""";

    private static final String EPISODES = """
            [
              {"id":9,"name":"Gray Matter","season":1,"number":1,"airdate":"2008-01-20"},
              {"id":10,"name":"Cat's in the Bag...","season":1,"number":2,"airdate":"2008-01-27"},
              {"id":11,"name":"4 Days Out","season":2,"number":1,"airdate":"2009-03-08"},
              {"id":12,"name":"Special","season":1,"number":null,"airdate":null}
            ]""";

    @Test
    void searchShowParsesScoredWrappersAndPremiereDate() throws Exception {
        Stub stub = new Stub(Map.of("/search/shows", SEARCH));
        List<ShowInfo> hits = new TvmazeClient(stub).searchShow("breaking bad", 0);

        assertEquals(2, hits.size()); // null-named entry dropped
        assertEquals("169", hits.get(0).id());
        assertEquals("Breaking Bad", hits.get(0).name());
        assertEquals(2008, hits.get(0).year());
        assertEquals(0, hits.get(1).year()); // no premiered → no year
        assertTrue(stub.requested.get(0).endsWith("q=breaking+bad"));
        assertTrue(stub.headers.get(0).containsKey("User-Agent")); // rate-limit etiquette
    }

    @Test
    void getSeasonFiltersOneSeasonAndSkipsUnnumbered() throws Exception {
        Stub stub = new Stub(Map.of("/shows/169/episodes", EPISODES));
        List<EpisodeInfo> episodes = new TvmazeClient(stub).getSeason("169", 1);

        assertEquals(2, episodes.size());
        assertEquals(new EpisodeInfo(1, 1, "Gray Matter", "2008-01-20"), episodes.get(0));
        assertEquals(2, episodes.get(1).episode());
        assertTrue(stub.requested.get(0).endsWith("/shows/169/episodes"));
    }

    @Test
    void movieSearchStaysEmptyBecauseTvmazeHasNoMovieApi() throws Exception {
        Stub stub = new Stub(Map.of());
        assertTrue(new TvmazeClient(stub).searchMovie("the dark knight", 2008).isEmpty());
        assertTrue(stub.requested.isEmpty()); // must not even hit the 404 route
    }

    @Test
    void datasourceNameIsTvmaze() {
        assertEquals("TVmaze", new TvmazeClient(new Stub(Map.of())).name());
    }
}
