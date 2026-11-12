package app.renami.tui;

import app.renami.gui.HistoryController;
import app.renami.rename.HistoryStore;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The History tab: every rename batch from the journal, newest first.
 * ↑/↓ select, u reverts the selected batch (verb-aware, like the GUI's
 * Undo batch), r reloads.
 */
public final class HistoryView implements TuiView {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final HistoryStore store = new HistoryStore();
    private List<HistoryStore.Batch> batches = List.of();
    private int selected;
    private String status = "↑/↓ select · u undo batch · r reload";

    @Override
    public String title() {
        return "History";
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public void onActivate() {
        reload();
    }

    private void reload() {
        try {
            batches = store.batches();
            selected = Math.min(selected, Math.max(0, batches.size() - 1));
        } catch (Exception e) {
            batches = List.of();
            status = "cannot read history: " + e.getMessage();
        }
    }

    @Override
    public boolean onKey(KeyStroke key) {
        switch (key.getKeyType()) {
            case ArrowUp -> selected = Math.max(0, selected - 1);
            case ArrowDown -> selected = Math.min(Math.max(0, batches.size() - 1), selected + 1);
            case PageUp -> selected = Math.max(0, selected - 10);
            case PageDown -> selected = Math.min(Math.max(0, batches.size() - 1), selected + 10);
            case Character -> {
                char c = key.getCharacter();
                if (c == 'r') {
                    reload();
                    status = "history reloaded (" + batches.size() + " batches)";
                } else if (c == 'u') {
                    undo();
                } else {
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void undo() {
        if (selected >= batches.size()) {
            return;
        }
        HistoryStore.Batch batch = batches.get(selected);
        if (batch.undone()) {
            status = "batch " + TIME.format(Instant.ofEpochMilli(batch.time())) + " is already undone";
            return;
        }
        try {
            HistoryController.UndoReport report = new HistoryController(store).undo(batch.id());
            reload();
            status = report.restored() + " of " + report.total() + " files restored";
        } catch (Exception e) {
            status = "undo failed: " + e.getMessage();
        }
    }

    @Override
    public void render(TextGraphics g, int width, int height) {
        if (batches.isEmpty()) {
            g.putString(2, 2, "no rename batches yet — rename something in the Rename tab first");
            return;
        }
        g.putString(2, 1, clip(String.format("%-17s %6s  %-8s %s",
                "Time", "Files", "Status", "Batch"), width - 4));
        int row = 2;
        for (int i = 0; i < batches.size() && row < height - 1; i++, row++) {
            HistoryStore.Batch batch = batches.get(i);
            String line = String.format("%-17s %6d  %-8s %s",
                    TIME.format(Instant.ofEpochMilli(batch.time())),
                    batch.ops().size(),
                    batch.undone() ? "undone" : "active",
                    batch.id().substring(0, 8));
            if (i == selected) {
                g.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.BLACK);
                g.setBackgroundColor(com.googlecode.lanterna.TextColor.ANSI.WHITE_BRIGHT);
                g.putString(2, row, clip(line, width - 4));
                g.setBackgroundColor(com.googlecode.lanterna.TextColor.ANSI.DEFAULT);
                g.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.DEFAULT);
            } else {
                g.putString(2, row, clip(line, width - 4));
            }
        }
    }

    private static String clip(String s, int width) {
        return s.length() <= width ? s : s.substring(0, Math.max(0, width - 1)) + "…";
    }
}
