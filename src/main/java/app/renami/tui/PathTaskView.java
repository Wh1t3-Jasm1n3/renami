package app.renami.tui;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared shape for the path-driven tabs (List, Checksum, Duplicates, …):
 * type a path, Enter runs {@link #compute} on a background thread, the
 * result lines fill the content area. Action keys reach
 * {@link #onResultKey} once results are on screen (Esc clears first to
 * edit the path again).
 */
abstract class PathTaskView implements TuiView {

    protected final StringBuilder path = new StringBuilder();
    protected volatile List<String> lines = List.of();
    protected volatile String status;
    private volatile boolean busy;
    private volatile int generation; // bumped by Escape to discard worker results
    private int scroll;
    private final String help;

    protected PathTaskView(String help) {
        this.help = help;
        this.status = help;
    }

    /** Produce the result lines; called on a worker thread. */
    protected abstract List<String> compute(Path input) throws Exception;

    /** Extra keys once results are shown (selection, apply, …). */
    protected boolean onResultKey(KeyStroke key) {
        return false;
    }

    /** Appended to the status line after a successful run. */
    protected String resultHelp() {
        return "";
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public boolean busy() {
        return busy;
    }

    protected void setStatus(String text) {
        status = text;
    }

    protected int scroll() {
        return scroll;
    }

    @Override
    public boolean onKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Tab || key.getKeyType() == KeyType.ReverseTab) {
            return false; // let the app switch tabs
        }
        if (busy && key.getKeyType() != KeyType.Escape) {
            return true; // swallow keys while the worker runs
        }
        switch (key.getKeyType()) {
            case Enter -> start();
            case Backspace -> {
                if (path.length() > 0) {
                    path.deleteCharAt(path.length() - 1);
                }
            }
            case Escape -> {
                if (!busy && lines.isEmpty() && path.length() == 0) {
                    return false; // nothing to clear: let the app quit
                }
                generation++; // discard any running worker's result
                busy = false;
                lines = List.of();
                path.setLength(0);
                scroll = 0;
                status = help;
            }
            case PageDown -> scroll = Math.min(scroll + 10, maxScroll());
            case PageUp -> scroll = Math.max(0, scroll - 10);
            default -> {
                Character c = key.getCharacter();
                if (!lines.isEmpty() && onResultKey(key)) {
                    // action key handled
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
            status = "Enter a path first.";
            return;
        }
        Path input = expandHome(raw);
        if (!Files.exists(input)) {
            status = "not found: " + input;
            return;
        }
        busy = true;
        scroll = 0;
        status = "working ...";
        int generation = this.generation;
        Thread worker = new Thread(() -> {
            try {
                List<String> out = compute(input);
                if (generation == this.generation) { // not cancelled meanwhile
                    lines = out;
                    status = out.size() + " results — PgUp/PgDn scroll" + resultHelp();
                    TuiApp.trace("worker done: " + out.size() + " lines");
                }
            } catch (Exception e) {
                if (generation == this.generation) {
                    lines = List.of();
                    status = "failed: " + e.getMessage();
                    TuiApp.trace("worker failed: " + e);
                }
            } finally {
                if (generation == this.generation) {
                    busy = false;
                }
            }
        }, "renami-tui-" + title().toLowerCase());
        worker.setDaemon(true);
        worker.start();
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - 1);
    }

    /** Run an extra background task (apply, download) with status feedback. */
    protected void busyWhile(String label, Runnable task) {
        busy = true;
        status = label;
        Thread worker = new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                status = "failed: " + e.getMessage();
            } finally {
                busy = false;
            }
        }, "renami-tui-" + title().toLowerCase() + "-action");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void render(TextGraphics g, int width, int height) {
        g.putString(1, 1, clip("Path:   " + path + "▌", width - 2));
        g.putString(1, 2, clip(help, width - 2));
        int row = 4;
        int visible = Math.max(0, height - row - 1);
        for (int i = scroll; i < lines.size() && row < height - 1; i++, row++) {
            g.putString(1, row, clip(lines.get(i), width - 2));
        }
        if (!lines.isEmpty() && (scroll > 0 || scroll + visible < lines.size())) {
            g.putString(width - 12, 2, clip(String.format("[%d/%d]",
                    Math.min(scroll + visible, lines.size()), lines.size()), 10));
        }
    }

    static Path expandHome(String raw) {
        return Path.of(raw.startsWith("~")
                ? System.getProperty("user.home") + raw.substring(1) : raw);
    }

    static String clip(String s, int width) {
        if (width <= 0) {
            return "";
        }
        return s.length() <= width ? s : s.substring(0, width - 1) + "…";
    }
}
