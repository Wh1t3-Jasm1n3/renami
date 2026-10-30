package app.renami.web.xem;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XemClientTest {

    private static final JsonObject RESPONSE = JsonParser.parseString("""
            {"status":"Success","data":{
              "1":{"id":111,"tvdbId":70328,"absolute":1},
              "104":{"id":999,"tvdbId":70328,"absolute":104},
              "junk":{"id":0,"absolute":0}
            }}
            """).getAsJsonObject();

    @Test
    void resolvesAbsoluteToTvdbEpisodeId() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        XemClient client = new XemClient((url, headers) -> {
            calls.incrementAndGet();
            assertEquals("https://thexem.de/xem/getMappedTVDB/70328", url);
            return RESPONSE;
        });

        assertEquals(Optional.of(999L), client.tvdbEpisodeId("70328", 104));
        assertEquals(Optional.of(111L), client.tvdbEpisodeId("70328", 1));
        assertEquals(Optional.empty(), client.tvdbEpisodeId("70328", 200));
        assertEquals(1, calls.get()); // mapping cached per series
    }

    @Test
    void emptyOrMalformedDataYieldsNoMappings() throws Exception {
        XemClient client = new XemClient((url, headers) ->
                JsonParser.parseString("{\"status\":\"Success\",\"data\":{}}").getAsJsonObject());
        assertEquals(Optional.empty(), client.tvdbEpisodeId("1", 1));

        JsonObject missingData = new JsonObject();
        assertTrue(XemClient.parseMapping(missingData).isEmpty());
    }
}
