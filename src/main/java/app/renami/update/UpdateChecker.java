package app.renami.update;

import app.renami.util.Version;
import app.renami.web.JsonGet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Map;

/**
 * Asks GitHub for the newest Renami release and compares it with the running version.
 * The same probe powers the GUI startup check, the Help ▸ Check for Updates menu,
 * {@code renami update} and (from 1.7.0) the TUI.
 */
public final class UpdateChecker {

    public static final String REPO = "Wh1t3-Jasm1n3/renami";
    private static final String LATEST_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";

    private final JsonGet http;

    public UpdateChecker(JsonGet http) {
        this.http = http;
    }

    /** Version baked into the jar manifest (Implementation-Version); "0.0.0-dev" for dev builds. */
    public static String currentVersion() {
        return Version.get();
    }

    public UpdateInfo check() throws IOException {
        JsonObject release = http.get(LATEST_URL, Map.of("User-Agent", "renami-updater"));
        String tag = release.has("tag_name") ? release.get("tag_name").getAsString() : null;
        if (tag != null) {
            tag = tag.replaceFirst("^v", "");
        }
        String appZip = null;
        String cliZip = null;
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets != null) {
            for (var element : assets) {
                JsonObject asset = element.getAsJsonObject();
                String name = asset.has("name") ? asset.get("name").getAsString() : "";
                String url = asset.has("browser_download_url")
                        ? asset.get("browser_download_url").getAsString() : null;
                if (url == null) {
                    continue;
                }
                if (name.endsWith("-macos.zip")) {
                    appZip = url;
                } else if (name.endsWith("-cli.zip")) {
                    cliZip = url;
                }
            }
        }
        return new UpdateInfo(currentVersion(), tag, appZip, cliZip);
    }
}
