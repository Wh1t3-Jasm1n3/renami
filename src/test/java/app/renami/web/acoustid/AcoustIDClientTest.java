package app.renami.web.acoustid;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcoustIDClientTest {

    private static final String FIXTURE = """
            {
              "status": "ok",
              "results": [
                {
                  "score": 0.94,
                  "recording": {
                    "id": "8f8d5ce4-65a5-4a63-b295-1a0b4d1d1b1e",
                    "title": "Bohemian Rhapsody",
                    "artists": [{"name": "Queen"}]
                  }
                },
                {
                  "score": 0.61,
                  "recording": {"id": "x", "title": "Ghost Title", "artists": []}
                }
              ]
            }
            """;

    @Test
    void parsesLookupResults() {
        List<AcoustIDClient.Track> tracks = AcoustIDClient.parse(FIXTURE);
        assertEquals(2, tracks.size());
        assertEquals(0.94, tracks.get(0).score(), 1e-9);
        assertEquals("Queen", tracks.get(0).artist());
        assertEquals("Bohemian Rhapsody", tracks.get(0).title());
        assertEquals("", tracks.get(1).artist());
    }

    @Test
    void emptyResultsParseToEmptyList() {
        assertTrue(AcoustIDClient.parse("{\"status\":\"ok\",\"results\":[]}").isEmpty());
        assertTrue(AcoustIDClient.parse("{\"status\":\"error\"}").isEmpty());
    }
}
