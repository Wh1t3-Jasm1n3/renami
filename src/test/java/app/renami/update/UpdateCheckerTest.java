package app.renami.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    private static JsonObject asset(String name, String url) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("browser_download_url", url);
        return json;
    }

    private static UpdateChecker checkerFor(String tag, JsonObject... assets) {
        return new UpdateChecker((url, headers) -> {
            JsonObject release = new JsonObject();
            if (tag != null) {
                release.addProperty("tag_name", tag);
            }
            JsonArray array = new JsonArray();
            for (JsonObject asset : assets) {
                array.add(asset);
            }
            release.add("assets", array);
            return release;
        });
    }

    @Test
    void picksMacAndCliAssetsAndDetectsNewerRelease() throws Exception {
        UpdateInfo info = checkerFor("v1.9.9",
                asset("Renami-1.9.9.dmg", "https://example/r.dmg"),
                asset("Renami-1.9.9-macos.zip", "https://example/r.zip"),
                asset("renami-1.9.9-cli.zip", "https://example/cli.zip")
        ).check();
        assertEquals("1.9.9", info.latest());
        assertEquals("https://example/r.zip", info.appZipUrl());
        assertEquals("https://example/cli.zip", info.cliZipUrl());
        assertTrue(info.available()); // dev builds report 0.0.0-dev
    }

    @Test
    void notAvailableWithoutMacAssetOrWhenNotNewer() throws Exception {
        assertFalse(checkerFor("v1.9.9",
                asset("renami-1.9.9-cli.zip", "https://example/cli.zip")
        ).check().available());
        assertFalse(checkerFor("v0.0.0").check().available()); // equal to dev version
    }

    @Test
    void skippedVersionMatchesExactly() throws Exception {
        UpdateInfo info = checkerFor("v1.9.9",
                asset("Renami-1.9.9-macos.zip", "https://example/r.zip")).check();
        assertTrue(info.skippedAs("1.9.9"));
        assertFalse(info.skippedAs("1.9.8"));
        assertFalse(info.skippedAs(null));
    }
}
