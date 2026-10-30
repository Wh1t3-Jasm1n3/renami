package app.renami.gui;

import app.renami.pipeline.RenameService;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.RenameExecutor;
import app.renami.rename.RenameOperation;
import app.renami.web.Datasource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless controller behind the rename panel: produces preview rows and applies
 * them. All Swing-free so it can be unit-tested.
 */
public final class RenameController {

    public record Row(Path source, Path target, String status) {
    }

    private final Datasource datasource;

    public RenameController(Datasource datasource) {
        this.datasource = datasource;
    }

    public List<Row> preview(List<Path> files, RenameService.Options options) throws Exception {
        RenameService service = new RenameService(datasource, System.err);
        List<RenameOperation> plan = service.plan(files, options);
        Set<Path> planned = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        for (RenameOperation op : plan) {
            planned.add(op.source());
            rows.add(new Row(op.source(), op.target(), op.isNoOp() ? "unchanged" : "rename"));
        }
        for (Path f : files) {
            if (!planned.contains(f)) {
                rows.add(new Row(f, f, "no match"));
            }
        }
        return rows;
    }

    public List<Row> apply(List<Row> preview, ConflictPolicy conflict) throws Exception {
        List<RenameOperation> operations = preview.stream()
                .filter(r -> r.status().equals("rename"))
                .map(r -> new RenameOperation(r.source(), r.target()))
                .toList();
        RenameExecutor executor = new RenameExecutor(conflict, false);
        List<Row> applied = new ArrayList<>();
        for (RenameExecutor.Result result : executor.apply(operations)) {
            applied.add(new Row(result.operation().source(), result.actualTarget(), result.status()));
        }
        for (Row row : preview) {
            if (!row.status().equals("rename")) {
                applied.add(row);
            }
        }
        return applied;
    }
}
