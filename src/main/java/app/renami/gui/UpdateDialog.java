package app.renami.gui;

import app.renami.update.UpdateChecker;
import app.renami.update.UpdateInfo;
import app.renami.update.Updater;
import app.renami.util.Config;
import app.renami.web.HttpJson;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

/**
 * One-click update UI: the startup check and Help ▸ Check for Updates share this flow.
 * "Update &amp; Restart" downloads the release zip, swaps Renami.app in place (old copy
 * removed only after the new one is fully in place) and relaunches through LaunchServices.
 */
public final class UpdateDialog {

    private UpdateDialog() {
    }

    /**
     * Quiet background check shortly after launch: shows the update prompt only when a
     * newer release exists and the user has not disabled checks (update.check=false)
     * or dismissed this exact version ("Not this version" → update.skip).
     */
    static void checkOnStartup(Frame owner) {
        if ("false".equalsIgnoreCase(Config.load().get("update.check"))) {
            return;
        }
        SwingWorker<UpdateInfo, Void> worker = new SwingWorker<>() {
            @Override
            protected UpdateInfo doInBackground() throws Exception {
                UpdateInfo info = new UpdateChecker(
                        new HttpJson(Config.dataDir().resolve("cache"), false)).check();
                if (!info.available()) {
                    return null;
                }
                return info.skippedAs(Config.load().get("update.skip")) ? null : info;
            }

            @Override
            protected void done() {
                try {
                    UpdateInfo info = get();
                    if (info != null) {
                        promptUpdate(owner, info);
                    }
                } catch (Exception silent) {
                    // a failed startup check must never disturb the app; the user can
                    // still trigger Help ▸ Check for Updates manually
                }
            }
        };
        worker.execute();
    }

    /** Help ▸ Check for Updates: always reports the outcome, even when up to date. */
    static void checkInteractively(Component parent) {
        JDialog progress = indeterminate(parent, "Checking for updates…");
        SwingWorker<UpdateInfo, Void> worker = new SwingWorker<>() {
            @Override
            protected UpdateInfo doInBackground() throws Exception {
                return new UpdateChecker(
                        new HttpJson(Config.dataDir().resolve("cache"), false)).check();
            }

            @Override
            protected void done() {
                progress.dispose();
                try {
                    UpdateInfo info = get();
                    if (info.available()) {
                        promptUpdate(parent, info);
                    } else {
                        JOptionPane.showMessageDialog(parent,
                                "You are running the latest version (" + info.current() + ").",
                                "Check for Updates", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent,
                            "Could not check for updates: " + rootMessage(ex)
                                    + "\n\nCheck your network connection, or visit\n"
                                    + "https://github.com/" + UpdateChecker.REPO + "/releases",
                            "Check for Updates", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private static void promptUpdate(Component parent, UpdateInfo info) {
        Path app = Updater.installedApp();
        String body = "<html><body style='width:300px'>Renami <b>" + info.latest()
                + "</b> is available (you are running " + info.current() + ")."
                + (app != null
                        ? "<br><br>The update installs over " + app + " and restarts Renami."
                        : "<br><br>Open the downloads page to install it.");
        Object[] options = app != null
                ? new Object[]{"Update & Restart", "Not this version", "Cancel"}
                : new Object[]{"Open Downloads", "Cancel"};
        int choice = JOptionPane.showOptionDialog(parent, body, "Renami " + info.latest()
                        + " is available",
                JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice != 0) {
            if (app != null && choice == 1) {
                Config.set("update.skip", info.latest()); // stay quiet about this version
            }
            return;
        }
        if (app == null) {
            RenamiFrame.openUrl("https://github.com/" + UpdateChecker.REPO + "/releases");
            return;
        }
        installAndRestart(parent, app, info);
    }

    private static void installAndRestart(Component parent, Path app, UpdateInfo info) {
        JDialog progress = indeterminate(parent, "Downloading Renami " + info.latest() + "…");
        SwingWorker<Path, Void> worker = new SwingWorker<>() {
            private Process relaunch;

            @Override
            protected Path doInBackground() throws Exception {
                // must be spawned before the swap: the JVM cannot spawn processes from a
                // bundle it has just replaced under itself
                relaunch = Updater.scheduleRestart(app);
                try {
                    return Updater.install(info.appZipUrl(), app);
                } catch (Exception failure) {
                    relaunch.destroy(); // cancel the pending relaunch; the old app stays
                    throw failure;
                }
            }

            @Override
            protected void done() {
                progress.dispose();
                try {
                    Path updated = get();
                    JOptionPane.showMessageDialog(parent,
                            "Renami " + info.latest() + " was installed at " + updated
                                    + ".\n\nRenami will now restart.",
                            "Update installed", JOptionPane.INFORMATION_MESSAGE);
                    System.exit(0); // the scheduled helper reopens the updated bundle
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent,
                            "The update failed and your current version was left in place: "
                                    + rootMessage(ex),
                            "Update failed", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private static JDialog indeterminate(Component parent, String message) {
        // modeless on purpose: setVisible(true) must return so the SwingWorker can start;
        // done() disposes the dialog when the work finishes
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent),
                "Renami", Dialog.ModalityType.MODELESS);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        panel.add(new JLabel(message), BorderLayout.NORTH);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        panel.add(bar, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setVisible(true);
        return dialog;
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String m = root.getMessage();
        return m == null || m.isBlank() ? root.getClass().getSimpleName() : m;
    }
}
