package app.renami.tui;

import app.renami.media.MediaScanner;

import java.nio.file.Path;
import java.util.List;

/** The List tab: every media file under a path, like {@code renami list}. */
public final class ListView extends PathTaskView {

    public ListView() {
        super("Enter = scan folder for media files");
    }

    @Override
    public String title() {
        return "List";
    }

    @Override
    protected List<String> compute(Path input) throws Exception {
        List<Path> files = MediaScanner.scan(input);
        return files.stream().map(f -> input.relativize(f).toString()).toList();
    }
}
