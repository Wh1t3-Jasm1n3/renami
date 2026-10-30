package app.renami.web.tvdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TheTVDBClientTest {

    @Test
    void parsesNumericEpisodeNumbers() {
        assertEquals(7, TheTVDBClient.parseNumber("7"));
        assertEquals(12, TheTVDBClient.parseNumber("12"));
    }

    @Test
    void parsesSplitEpisodeNumbers() {
        assertEquals(12, TheTVDBClient.parseNumber("12:1"));
        assertEquals(12, TheTVDBClient.parseNumber("12:2"));
    }

    @Test
    void rejectsSpecials() {
        assertEquals(-1, TheTVDBClient.parseNumber(null));
        assertEquals(-1, TheTVDBClient.parseNumber(""));
        assertEquals(-1, TheTVDBClient.parseNumber("0"));
        assertEquals(-1, TheTVDBClient.parseNumber("abc"));
    }
}
