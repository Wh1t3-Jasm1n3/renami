package app.renami.tui;

import app.renami.update.UpdateChecker;
import app.renami.update.UpdateInfo;
import app.renami.update.Updater;
import app.renami.util.Config;
import app.renami.web.HttpJson;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Update tab: same shared core as the GUI dialog and `renami update`.
 * c checks GitHub, i installs the newest release over the bundled app.
 */
public final class UpdateView implements TuiView {

    private UpdateInfo info;
    private String status = "c = check GitHub for a newer release · i = install it";

    @Override
    public String title() {
        return "Update";
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public boolean onKey(KeyStroke key) {
        Character c = key.getCharacter();
        if (c == null) {
            return false;
        }
        if (c == 'c') {
            check();
        } else if (c == 'i') {
            install();
        } else {
            return false;
        }
        return true;
    }

    private void check() {
        status = "checking " + UpdateChecker.REPO + " ...";
        try {
            info = new UpdateChecker(new HttpJson(Config.dataDir().resolve("cache"), false)).check();
            status = info.available()
                    ? "Renami " + info.latest() + " available — press i to install"
                    : "you are running the latest version (" + info.current() + ")";
        } catch (Exception e) {
            status = "check failed: " + e.getMessage();
        }
    }

    private void install() {
        if (info == null || !info.available()) {
            status = "nothing to install — press c to check first";
            return;
        }
        Path target = Updater.installedApp();
        if (target == null) {
            Path applications = Path.of("/Applications", "Renami.app");
            target = Files.isDirectory(applications) ? applications : null;
        }
        if (target == null) {
            status = "no Renami.app to update — download: " + info.appZipUrl();
            return;
        }
        status = "downloading and installing over " + target + " ...";
        try {
            Updater.install(info.appZipUrl(), target);
            status = "Renami " + info.latest() + " installed at " + target + " — restart to use it";
        } catch (Exception e) {
            status = "update failed: " + e.getMessage();
        }
    }

    @Override
    public void render(TextGraphics g, int width, int height) {
        g.putString(2, 1, "Current version: " + UpdateChecker.currentVersion());
        if (info != null) {
            g.putString(2, 2, "Latest release:  " + info.latest());
            if (info.available()) {
                g.putString(2, 3, "→ press i to install Renami " + info.latest()
                        + " over the installed app");
            }
        }
        g.putString(2, 5, "Checks never send anything but the release lookup; no telemetry.");
    }
}
