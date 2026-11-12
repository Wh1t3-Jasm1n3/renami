package app.renami.web.opensubtitles;

import app.renami.web.JsonGet;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Request-shape tests for the OpenSubtitles v2 REST API: the consumer key is
 * sent as the {@code Apikey} header (never in the URL), and search results are
 * parsed from the documented {@code data[].attributes} envelope.
 */
class OpenSubtitlesClientTest {

    private static final class Stub implements JsonGet {
        private String url;
        private Map<String, String> headers;
        private String answer = "{\"data\":[]}";
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
    void searchSendsConsumerKeyAsApikeyHeader() throws Exception {
        Stub stub = new Stub();
        new OpenSubtitlesClient("consumer-key-123", stub).search("Breaking Bad", "en");
        assertTrue(stub.url.startsWith("https://api.opensubtitles.com/api/v1/subtitles?"), stub.url);
        assertTrue(stub.url.contains("query=Breaking+Bad"), stub.url);
        assertTrue(stub.url.contains("languages=en"), stub.url);
        assertEquals("consumer-key-123", stub.headers.get("Apikey"));
        assertTrue(!stub.url.contains("consumer-key-123"), "key must not leak into the URL: " + stub.url);
    }

    @Test
    void hashSearchUsesLegacyEndpointWithHexHash() throws Exception {
        Stub stub = new Stub();
        new OpenSubtitlesClient("consumer-key-123", stub).searchByHash(0x123456789abcdefL, "de");
        assertTrue(stub.url.contains("/subtitles/search/legacy?moviehash=123456789abcdef"), stub.url);
        assertTrue(stub.url.contains("languages=de"), stub.url);
        assertEquals("consumer-key-123", stub.headers.get("Apikey"));
    }

    @Test
    void candidatesAreParsedFromTheAttributesEnvelope() throws Exception {
        Stub stub = new Stub();
        stub.answer = """
                {"data":[
                  {"id":987654,"attributes":{"title":"Breaking Bad 1x01","language":"en",
                    "download_count":4212,"format":"srt"}}
                ]}""";
        List<OpenSubtitlesClient.Candidate> hits =
                new OpenSubtitlesClient("k", stub).search("Breaking Bad", "en");
        assertEquals(1, hits.size());
        assertEquals(987654, hits.get(0).id());
        assertEquals("Breaking Bad 1x01", hits.get(0).title());
        assertEquals("srt", hits.get(0).format());
        assertEquals(4212, hits.get(0).downloads());
    }

    @Test
    void badKeySurfacesTheAuthenticationFailure() {
        Stub stub = new Stub();
        stub.failure = new IOException("authentication failed for https://api.opensubtitles.com/api/v1/subtitles"
                + " — check your API key");
        IOException e = assertThrows(IOException.class,
                () -> new OpenSubtitlesClient("wrong", stub).search("Breaking Bad", "en"));
        assertTrue(e.getMessage().contains("check your API key"), e.getMessage());
    }
}
