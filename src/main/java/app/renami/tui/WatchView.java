package app.renami.tui;

import app.renami.gui.WatchController;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.Datasources;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Watch tab: same {@link WatchController} as the GUI and {@code renami
 * watch}. Enter starts the watch on the typed folder, s stops it, a toggles
 * dry-run vs. real transfers for the next start.
 */
public final class WatchView implements TuiView {

    private final StringBuilder path = new StringBuilder();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private volatile String status =
            "Type a folder, Enter starts the watch. a = toggle dry-run (before typing).";
    private volatile boolean watching;
    private volatile boolean apply;
    private volatile WatchController controller;

    @Override
    public String title() {
        return "Watch";
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public boolean busy() {
        return watching;
    }

    @Override
    public boolean onKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Tab || key.getKeyType() == KeyType.ReverseTab) {
            return false;
        }
        if (watching) {
            if (Character.valueOf('s').equals(key.getCharacter())) {
                WatchController w = controller;
                if (w != null) {
                    w.stop();
                }
                status = "stopping ...";
            } else if (Character.valueOf('a').equals(key.getCharacter())) {
                apply = !apply;
                status = apply ? "APPLY mode — files will really move" : "dry-run mode";
            }
            return true;
        }
        switch (key.getKeyType()) {
            case Enter -> start();
            case Backspace -> {
                if (path.length() > 0) {
                    path.deleteCharAt(path.length() - 1);
                }
            }
            case Escape -> {
                if (path.length() == 0 && events.isEmpty()) {
                    return false; // nothing to clear: let the app quit
                }
                events.clear();
                path.setLength(0);
                status = "Type a folder, Enter starts the watch. a = toggle dry-run (before typing).";
            }
            default -> {
                Character c = key.getCharacter();
                if (Character.valueOf('a').equals(c) && path.length() == 0) {
                    // only a mode toggle while idle with no path typed: otherwise
                    // 'a' belongs to the path text (paths are full of them)
                    apply = !apply;
                    status = apply ? "APPLY mode — files will really move" : "dry-run mode";
                } else if (c != null && c >= ' ') {
                    path.append(c);
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    private void start() {
        String raw = path.toString().trim();
        if (raw.isEmpty()) {
            status = "Enter a folder to watch first.";
            return;
        }
        Path input = PathTaskView.expandHome(raw);
        if (!Files.isDirectory(input)) {
            status = "not a folder: " + input;
            return;
        }
        watching = true;
        events.clear();
        status = "watching " + input + (apply ? " (APPLY)" : " (dry-run)") + " — s stops";
        WatchController.Options options = new WatchController.Options(
                "auto", null, "rename", ConflictPolicy.INDEX, 0.60, "default", true, apply);
        WatchController w = new WatchController(new HistoryStore());
        controller = w;
        Thread worker = new Thread(() -> {
            try {
                Datasource datasource = Datasources.create(Config.load(), "auto", false);
                w.run(input, 5, options, datasource, events::add);
                status = "watch stopped — " + events.size() + " events";
            } catch (Exception e) {
                status = "watch failed: " + e.getMessage();
            } finally {
                watching = false;
            }
        }, "renami-tui-watch");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void render(TextGraphics g, int width, int height) {
        g.putString(1, 1, PathTaskView.clip("Watch:  " + path + (watching ? "" : "▌"), width - 2));
        g.putString(1, 2, PathTaskView.clip(
                "mode: " + (apply ? "APPLY (files really move)" : "dry-run")
                        + " · Enter start · s stop · a toggle", width - 2));
        int row = 4;
        int visible = Math.max(0, height - row - 1);
        int first = Math.max(0, events.size() - visible); // follow the tail
        for (int i = first; i < events.size() && row < height - 1; i++, row++) {
            g.putString(1, row, PathTaskView.clip(events.get(i), width - 2));
        }
    }
}
