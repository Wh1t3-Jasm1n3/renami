package app.renami.media;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaInfoVarsTest {

    @Test
    void extractsFromMediaInfoJson() {
        var json = JsonParser.parseString("""
                {"media": {"track": [
                  {"@type": "General", "FileSize": "123456789", "Duration": "2700000"},
                  {"@type": "Video", "Format": "AVC", "Width": "1920", "Height": "1080", "BitDepth": "10"},
                  {"@type": "Audio", "Format": "AC-3", "Channels": "6"}
                ]}}
                """).getAsJsonObject();
        Map<String, Object> vars = MediaInfoVars.extract(json);
        assertEquals("AVC", vars.get("vf"));
        assertEquals("1920x1080", vars.get("vres"));
        assertEquals("AC-3", vars.get("ac"));
        assertEquals(123456789L, vars.get("size"));
        assertEquals(10L, vars.get("bitdepth"));
    }

    @Test
    void extractsFromFfprobeJson() {
        var json = JsonParser.parseString("""
                {"streams": [
                  {"codec_type": "video", "codec_name": "h264", "width": 1280, "height": 720},
                  {"codec_type": "audio", "codec_name": "aac"}
                ], "format": {"duration": "1234.56", "size": "98765"}}
                """).getAsJsonObject();
        Map<String, Object> vars = MediaInfoVars.extract(json);
        assertEquals("h264", vars.get("vf"));
        assertEquals("1280x720", vars.get("vres"));
        assertEquals("aac", vars.get("ac"));
        assertEquals(98765L, vars.get("size"));
        assertEquals(1234L, vars.get("duration"));
    }

    @Test
    void missingFieldsYieldNoVars() {
        var json = JsonParser.parseString("{\"media\": {\"track\": [{\"@type\": \"General\"}]}}")
                .getAsJsonObject();
        assertEquals(Map.of(), MediaInfoVars.extract(json));
    }
}
