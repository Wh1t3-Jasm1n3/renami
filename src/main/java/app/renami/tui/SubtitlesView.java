package app.renami.tui;

import app.renami.gui.SubtitlesController;
import app.renami.media.MediaScanner;
import app.renami.util.Config;
import app.renami.web.HttpJson;
import app.renami.web.opensubtitles.OpenSubtitlesClient;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The Subtitles tab: search OpenSubtitles for every subtitle file's movie
 * (same flow as the GUI panel). Needs the same API key; d downloads the
 * selected candidate next to its video.
 */
public final class SubtitlesView extends PathTaskView {

    private List<SubtitlesController.Row> rows = List.of();
    private int selected;

    public SubtitlesView() {
        super("Enter = search OpenSubtitles (needs opensubtitles.api.key)");
    }

    @Override
    public String title() {
        return "Subtitles";
    }

    @Override
    protected String resultHelp() {
        return " · ↑/↓ select · d download selected";
    }

    @Override
    protected List<String> compute(Path input) throws Exception {
        String key = Config.load().get("opensubtitles.api.key");
        if (key == null) {
            throw new IllegalStateException("no OpenSubtitles API key — set it in the GUI "
                    + "(File ▸ API keys) or ~/.renami/config.properties");
        }
        List<Path> files = MediaScanner.scan(input);
        OpenSubtitlesClient client =
                new OpenSubtitlesClient(key, new HttpJson(Config.dataDir().resolve("cache"), true));
        rows = new SubtitlesController(client).preview(files, "en");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            OpenSubtitlesClient.Candidate c = rows.get(i).candidate();
            out.add(String.format("%-2d %s  →  %s [%s, %s, %d downloads]",
                    i + 1, rows.get(i).source().getFileName(), c.title(),
                    c.language(), c.format(), c.downloads()));
        }
        selected = 0;
        return out;
    }

    @Override
    protected boolean onResultKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.ArrowUp) {
            selected = Math.max(0, selected - 1);
        } else if (key.getKeyType() == KeyType.ArrowDown) {
            selected = Math.min(Math.max(0, rows.size() - 1), selected + 1);
        } else if (Character.valueOf('d').equals(key.getCharacter()) && !rows.isEmpty()) {
            SubtitlesController.Row row = rows.get(selected);
            OpenSubtitlesClient client = new OpenSubtitlesClient(
                    Config.load().get("opensubtitles.api.key"),
                    new HttpJson(Config.dataDir().resolve("cache"), true));
            busyWhile("downloading " + row.candidate().title() + " ...", () -> {
                try {
                    new SubtitlesController(client).download(row);
                    setStatus("saved: " + row.target());
                } catch (Exception e) {
                    setStatus("download failed: " + e.getMessage());
                }
            });
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void render(com.googlecode.lanterna.graphics.TextGraphics g, int width, int height) {
        super.render(g, width, height);
        if (!rows.isEmpty() && selected >= scroll()) {
            g.putString(0, 4 + selected - scroll(), "▸");
        }
    }
}
