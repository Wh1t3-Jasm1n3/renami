package app.renami.cli;

import app.renami.update.UpdateChecker;
import app.renami.update.UpdateInfo;
import app.renami.update.Updater;
import app.renami.util.Config;
import app.renami.web.HttpJson;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code renami update} — check GitHub for a newer release and install it over the
 * installed Renami.app. The same core powers the GUI dialog and (from 1.7.0) the TUI.
 */
@Command(name = "update", description = "Check for a newer release and install it.",
        mixinStandardHelpOptions = true)
public class UpdateCommand implements Callable<Integer> {

    @Option(names = "--check", description = "Only report what is available; do not install.")
    boolean checkOnly;

    @Override
    public Integer call() throws Exception {
        UpdateInfo info = new UpdateChecker(
                new HttpJson(Config.dataDir().resolve("cache"), false)).check(); // never cached: fresh release info
        System.out.println("Renami " + info.current() + " → latest release: " + info.latest());
        if (!info.available()) {
            System.out.println("You are running the latest version.");
            return 0;
        }
        System.out.println("Update available: Renami " + info.latest());
        if (checkOnly) {
            System.out.println("Download: " + info.appZipUrl());
            System.out.println("Run `renami update` to install it.");
            return 0;
        }
        Path target = updateTarget();
        if (target == null) {
            System.out.println("No Renami.app found to update (checked this process and "
                    + "/Applications/Renami.app).");
            System.out.println("Download: " + info.appZipUrl());
            return 0;
        }
        System.out.println("Downloading and installing over " + target + " ...");
        Updater.install(info.appZipUrl(), target);
        System.out.println("Renami " + info.latest() + " installed. Restart Renami to use it.");
        return 0;
    }

    /** The app this process runs from, else /Applications/Renami.app when present. */
    private static Path updateTarget() {
        Path own = Updater.installedApp();
        if (own != null) {
            return own;
        }
        Path applications = Path.of("/Applications", "Renami.app");
        return Files.isDirectory(applications) ? applications : null;
    }
}
