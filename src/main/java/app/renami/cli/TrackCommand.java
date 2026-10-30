package app.renami.cli;

import app.renami.audio.Fingerprinter;
import app.renami.media.MediaScanner;
import app.renami.pipeline.RenameService;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.RenameExecutor;
import app.renami.rename.RenameOperation;
import app.renami.rename.TransferAction;
import app.renami.util.Config;
import app.renami.web.acoustid.AcoustIDClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Music mode: fingerprint audio files with fpcalc, identify them via AcouSTID
 * (MusicBrainz), and rename to "Artist - Title".
 */
@Command(name = "track", description = "Identify audio files by fingerprint and rename them.", mixinStandardHelpOptions = true)
public class TrackCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "INPUT", description = "audio file or folder")
    Path input;

    @Option(names = "--apply", description = "perform the rename (default is dry-run)")
    boolean apply;

    @Option(names = "--min-score", defaultValue = "0.80", description = "minimum fingerprint match score 0..1 (default: ${DEFAULT-VALUE})")
    double minScore;

    @Option(names = "--format", defaultValue = "{a} - {t}", description = "rename template (default: ${DEFAULT-VALUE})")
    String format;

    @Override
    public Integer call() throws Exception {
        if (!Fingerprinter.isAvailable()) {
            System.err.println("error: fpcalc not found — install it with `brew install chromaprint`");
            return 3;
        }
        AcoustIDClient client;
        try {
            client = createClient(Config.load());
        } catch (IllegalStateException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }

        List<Path> files = MediaScanner.scan(input).stream().filter(MediaScanner::isAudio).toList();
        if (files.isEmpty()) {
            System.err.println("no audio files found under " + input);
            return 1;
        }

        List<RenameOperation> plan = new ArrayList<>();
        for (Path file : files) {
            Fingerprinter.Fingerprint fingerprint = Fingerprinter.of(file);
            AcoustIDClient.Track best = client.lookup(fingerprint).stream()
                    .max(Comparator.comparingDouble(AcoustIDClient.Track::score))
                    .orElse(null);
            if (best == null || best.score() < minScore) {
                System.out.println("[no match] " + file.getFileName());
                continue;
            }
            System.out.println("match: \"" + file.getFileName() + "\"  →  " + best.artist() + " - " + best.title()
                    + "  (score " + String.format("%.2f", best.score()) + ")");
            var vars = new java.util.LinkedHashMap<String, Object>();
            vars.put("a", best.artist());
            vars.put("t", best.title());
            String name = RenameService.sanitize(app.renami.format.FormatEngine.format(format, vars));
            plan.add(new RenameOperation(file, file.resolveSibling(name + "." + MediaScanner.extension(file))));
        }

        if (plan.isEmpty()) {
            System.out.println("nothing to do");
            return 0;
        }
        if (!apply) {
            for (RenameOperation op : plan) {
                System.out.println("[dry-run] " + op.source().getFileName() + "  →  " + op.target().getFileName());
            }
            System.out.println();
            System.out.println(plan.size() + " operations planned. Re-run with --apply to execute.");
            return 0;
        }
        List<RenameExecutor.Result> results = new RenameExecutor(ConflictPolicy.FAIL, TransferAction.RENAME).apply(plan);
        createHistoryStore().record(results.stream()
                .filter(RenameExecutor.Result::applied)
                .map(RenameExecutor.Result::operation)
                .toList(), TransferAction.RENAME);
        for (RenameExecutor.Result r : results) {
            System.out.println("[" + r.status() + "] " + r.operation().source().getFileName() + "  →  " + r.actualTarget().getFileName());
        }
        System.out.println();
        System.out.println("Use `renami undo` to revert.");
        return 0;
    }

    /** Overridable for tests. */
    AcoustIDClient createClient(Config config) {
        String key = config.get("acoustid.api.key");
        if (key == null) {
            throw new IllegalStateException(
                    "no AcouSTID API key set — set RENAMI_ACOUSTID_API_KEY or acoustid.api.key in ~/.renami/config.properties");
        }
        return new AcoustIDClient(key);
    }

    HistoryStore createHistoryStore() {
        return new HistoryStore();
    }
}
