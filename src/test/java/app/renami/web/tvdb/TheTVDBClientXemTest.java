package app.renami.web.tvdb;

import app.renami.web.model.EpisodeInfo;
import app.renami.web.xem.XemClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheTVDBClientXemTest {

    private static final class Stub extends TheTVDBClient {
        private final List<EpisodeInfo> absoluteList;
        private final AtomicBoolean fetchCalled = new AtomicBoolean();

        Stub(List<EpisodeInfo> absoluteList, Optional<Long> xemMapping) {
            super("key", null);
            this.absoluteList = absoluteList;
            this.xem = new XemClient((url, headers) -> xemJson(xemMapping));
        }

        private static JsonObject xemJson(Optional<Long> mapping) {
            if (mapping.isEmpty()) {
                return JsonParser.parseString("{\"status\":\"Success\",\"data\":{}}").getAsJsonObject();
            }
            return JsonParser.parseString("{\"status\":\"Success\",\"data\":{\"55\":{\"id\":"
                    + mapping.get() + ",\"tvdbId\":79562,\"absolute\":55}}}").getAsJsonObject();
        }

        @Override
        public List<EpisodeInfo> getSeason(String showId, int season, String order) {
            return absoluteList;
        }

        @Override
        EpisodeInfo fetchEpisode(long tvdbEpisodeId) throws IOException {
            fetchCalled.set(true);
            return new EpisodeInfo(2, 5, "XEM Mapped Episode", "2011-08-10");
        }
    }

    @Test
    void directAbsoluteHitSkipsXem() throws Exception {
        Stub stub = new Stub(List.of(new EpisodeInfo(1, 55, "Direct Hit", "2011-01-01")), Optional.of(999L));
        EpisodeInfo mapped = stub.mapAbsoluteEpisode("79562", 55);
        assertEquals("Direct Hit", mapped.name());
        assertEquals(false, stub.fetchCalled.get());
    }

    @Test
    void xemFallbackWhenAbsoluteOrderMisses() throws Exception {
        Stub stub = new Stub(List.of(), Optional.of(999L));
        EpisodeInfo mapped = stub.mapAbsoluteEpisode("79562", 55);
        assertEquals(2, mapped.season());
        assertEquals(5, mapped.episode());
        assertEquals("XEM Mapped Episode", mapped.name());
        assertTrue(stub.fetchCalled.get());
    }

    @Test
    void noXemMappingYieldsNull() throws Exception {
        Stub stub = new Stub(List.of(), Optional.empty());
        assertNull(stub.mapAbsoluteEpisode("79562", 55));
        assertEquals(false, stub.fetchCalled.get());
    }

    @Test
    void nonNumericShowIdNeverQueriesXem() throws Exception {
        Stub stub = new Stub(List.of(), Optional.of(999L));
        assertNull(stub.mapAbsoluteEpisode("tmdb-12345", 55));
        assertEquals(false, stub.fetchCalled.get());
    }
}
