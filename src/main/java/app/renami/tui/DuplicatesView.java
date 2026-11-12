package app.renami.tui;

import app.renami.cli.DupeCommand;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The Duplicates tab: size → partial hash → full SHA-256, like {@code renami dupe}. */
public final class DuplicatesView extends PathTaskView {

    public DuplicatesView() {
        super("Enter = find duplicate files (three-phase scan)");
    }

    @Override
    public String title() {
        return "Duplicates";
    }

    @Override
    protected List<String> compute(Path input) throws Exception {
        List<List<Path>> groups = DupeCommand.findDuplicates(input);
        if (groups.isEmpty()) {
            return List.of("no duplicates found under " + input);
        }
        List<String> out = new ArrayList<>();
        int index = 1;
        for (List<Path> group : groups) {
            out.add("group " + index++ + " (" + group.size() + " copies):");
            for (Path p : group) {
                out.add("  " + p);
            }
        }
        return out;
    }
}
