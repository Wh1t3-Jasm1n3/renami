package app.renami.tui;

import app.renami.media.MediaScanner;
import app.renami.pipeline.RenameService;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.RenameExecutor;
import app.renami.rename.RenameOperation;
import app.renami.rename.TransferAction;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.Datasources;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The Rename tab: type a path, Enter scans and plans (the same
 * {@link RenameService} pipeline the CLI and GUI use), r applies with a
 * confirmation, and every applied batch lands in the history journal.
 */
public final class RenameView implements TuiView {

    private final String mode;
    private final String tabTitle;
    private final StringBuilder path = new StringBuilder();
    private List<Path> files = List.of();
    private volatile List<RenameOperation> plan = List.of();
    private volatile String status = "Type a file or folder path, press Enter to scan and plan.";
    private boolean confirmApply;
    private volatile boolean busy;
    private volatile boolean applying; // a rename is in flight: do not cancel
    private volatile int generation;   // bumped by Escape to discard a running match
    private int scroll;

    public RenameView() {
        this("auto", "Rename");
    }

    /** The Episodes tab is the same pipeline forced into episode matching. */
    public RenameView(String mode, String tabTitle) {
        this.mode = mode;
        this.tabTitle = tabTitle;
    }

    @Override
    public String title() {
        return tabTitle;
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public boolean busy() {
        return busy;
    }

    @Override
    public boolean onKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Tab || key.getKeyType() == KeyType.ReverseTab) {
            return false; // let the app switch tabs
        }
        if (busy) {
            // Escape cancels a running match; other keys are swallowed.
            // A rename in flight is never cancelled (files are already moving).
            if (key.getKeyType() == KeyType.Escape && !applying) {
                generation++;
                busy = false;
                files = List.of();
                plan = List.of();
                path.setLength(0);
                status = "Cleared. Type a file or folder path, press Enter to scan and plan.";
            }
            return true;
        }
        if (confirmApply) {
            Character c = key.getCharacter();
            if (c != null && (c == 'y' || c == 'Y')) {
                apply();
            } else {
                confirmApply = false;
                status = "Rename cancelled.";
            }
            return true;
        }
        switch (key.getKeyType()) {
            case Enter -> plan();
            case Backspace -> {
                if (path.length() > 0) {
                    path.deleteCharAt(path.length() - 1);
                }
            }
            case Escape -> {
                if (files.isEmpty() && plan.isEmpty() && path.length() == 0) {
                    return false; // nothing to clear: let the app quit
                }
                files = List.of();
                plan = List.of();
                path.setLength(0);
                status = "Cleared. Type a file or folder path, press Enter to scan and plan.";
            }
            case PageDown -> scroll = Math.min(scroll + 10, Math.max(0, plan.size() - 1));
            case PageUp -> scroll = Math.max(0, scroll - 10);
            default -> {
                Character c = key.getCharacter();
                if (c != null && c >= ' ') {
                    if (c == 'r' && !plan.isEmpty()) {
                        confirmApply = true;
                        status = "Apply " + plan.size() + " renames? y = yes, any other key = cancel";
                    } else {
                        path.append(c);
                    }
                }
            }
        }
        return true;
    }

    private void plan() {
        String raw = path.toString().trim();
        if (raw.isEmpty()) {
            status = "Enter a path first.";
            return;
        }
        Path input = Path.of(raw.startsWith("~")
                ? System.getProperty("user.home") + raw.substring(1) : raw);
        if (!Files.exists(input)) {
            status = "not found: " + input;
            return;
        }
        try {
            files = MediaScanner.scan(input);
        } catch (Exception e) {
            status = "scan failed: " + e.getMessage();
            return;
        }
        if (files.isEmpty()) {
            plan = List.of();
            status = "no media files found under " + input;
            return;
        }
        status = "matching " + files.size() + " files ...";
        scroll = 0;
        busy = true;
        int generation = this.generation;
        Thread worker = new Thread(() -> {
            try {
                Datasource datasource = Datasources.create(Config.load(), "auto", false);
                RenameService service = new RenameService(datasource,
                        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
                List<RenameOperation> planned =
                        service.plan(files, new RenameService.Options(mode, null, 0.60));
                if (generation == this.generation) { // not cancelled meanwhile
                    plan = planned;
                    status = plan.size() + " of " + files.size()
                            + " names planned — press r to rename";
                }
            } catch (Exception e) {
                if (generation == this.generation) {
                    plan = List.of();
                    status = "match failed: " + e.getMessage();
                }
            } finally {
                if (generation == this.generation) {
                    busy = false;
                }
            }
        }, "renami-tui-" + tabTitle.toLowerCase());
        worker.setDaemon(true);
        worker.start();
    }

    private void apply() {
        List<RenameOperation> toApply = plan;
        plan = List.of();
        busy = true;
        applying = true;
        status = "renaming " + toApply.size() + " files ...";
        Thread worker = new Thread(() -> {
            try {
                RenameExecutor executor =
                        new RenameExecutor(ConflictPolicy.FAIL, TransferAction.RENAME);
                List<RenameExecutor.Result> results = executor.apply(toApply);
                List<RenameOperation> applied = results.stream()
                        .filter(RenameExecutor.Result::applied)
                        .map(RenameExecutor.Result::operation)
                        .toList();
                new HistoryStore().record(applied, TransferAction.RENAME);
                long skipped = results.size() - applied.size();
                status = applied.size() + " files renamed"
                        + (skipped > 0 ? " (" + skipped + " skipped: conflicts)" : "")
                        + ". Use the History tab to undo.";
                files = List.of();
            } catch (Exception e) {
                status = "rename failed: " + e.getMessage();
            } finally {
                busy = false;
                applying = false;
            }
        }, "renami-tui-apply");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void render(TextGraphics g, int width, int height) {
        g.putString(1, 1, clip("Path:   " + path + "▌", width - 2));
        g.putString(1, 2, clip("Files:  " + files.size() + "     Planned: " + plan.size(), width - 2));
        g.putString(1, 3, clip("Enter = scan + plan · r = rename · PgUp/PgDn scroll · Esc = clear",
                width - 2));
        int row = 5;
        int visible = Math.max(0, height - row - 1);
        for (int i = scroll; i < plan.size() && row < height - 1; i++, row++) {
            RenameOperation op = plan.get(i);
            String line = String.format("%3d  %s  →  %s", i + 1,
                    op.source().getFileName(), op.target().getFileName());
            g.putString(1, row, clip(line, width - 2));
        }
        if (plan.isEmpty() && !files.isEmpty()) {
            g.putString(1, row, "nothing matched confidently (min score 0.60)");
        }
        if (scroll > 0 || scroll + visible < plan.size()) {
            g.putString(width - 12, 3, clip(String.format("[%d/%d]", Math.min(scroll + visible,
                    plan.size()), plan.size()), 10));
        }
    }

    private static String clip(String s, int width) {
        if (width <= 0) {
            return "";
        }
        return s.length() <= width ? s : s.substring(0, width - 1) + "…";
    }
}
