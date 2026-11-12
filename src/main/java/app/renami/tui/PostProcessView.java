package app.renami.tui;

import app.renami.gui.PostProcessController;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.RenameOperation;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.Datasources;
import com.googlecode.lanterna.input.KeyStroke;

import java.nio.file.Path;
import java.util.List;

/**
 * The Post-Process tab: extract archives, match, then move everything into
 * the structured library layout (same defaults as the GUI panel: move,
 * INDEX conflicts, companion files along). p applies, with a y confirm.
 */
public final class PostProcessView extends PathTaskView {

    private static final PostProcessController.Options OPTIONS =
            new PostProcessController.Options("auto",
                    "{n}/Season {s}/{n} - {s00e00} - {t}",
                    "{n} ({y})/{n} ({y})",
                    "move", ConflictPolicy.INDEX, 0.60, "default", false, true);

    private final PostProcessController controller = new PostProcessController(new HistoryStore());
    private volatile PostProcessController.Preview preview;
    private boolean confirmApply;

    public PostProcessView() {
        super("Enter = extract archives + plan the organize (in place)");
    }

    @Override
    public String title() {
        return "Post-Process";
    }

    @Override
    protected String resultHelp() {
        return " · p = move files into place";
    }

    @Override
    protected List<String> compute(Path input) throws Exception {
        Datasource datasource = Datasources.create(Config.load(), "auto", false);
        PostProcessController.Preview p =
                controller.plan(input, null, OPTIONS, datasource, this::setStatus);
        preview = p;
        return p.plan().stream()
                .map(op -> op.source() + "  →  " + op.target())
                .toList();
    }

    @Override
    protected boolean onResultKey(KeyStroke key) {
        Character c = key.getCharacter();
        if (confirmApply) {
            if (Character.valueOf('y').equals(c)) {
                confirmApply = false;
                apply();
            } else {
                confirmApply = false;
                setStatus("cancelled — nothing moved");
            }
            return true;
        }
        if (Character.valueOf('p').equals(c) && preview != null && !preview.plan().isEmpty()) {
            confirmApply = true;
            setStatus("Move " + preview.plan().size() + " files into place? y = yes, any other key = cancel");
            return true;
        }
        return false;
    }

    private void apply() {
        PostProcessController.Preview p = preview;
        preview = null;
        busyWhile("moving files ...", () -> {
            try {
                List<RenameOperation> done = controller.apply(p, OPTIONS, null, this::setStatus)
                        .stream().filter(r -> r.applied()).map(r -> r.operation()).toList();
                setStatus(done.size() + " files organized — use the History tab to undo");
                lines = List.of();
            } catch (Exception e) {
                setStatus("post-process failed: " + e.getMessage());
            }
        });
    }
}
