package app.renami.gui;

import app.renami.archive.ArchiveTool;
import app.renami.media.CompanionFiles;
import app.renami.media.MediaScanner;
import app.renami.pipeline.RenameService;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.RenameExecutor;
import app.renami.rename.RenameOperation;
import app.renami.rename.TransferAction;
import app.renami.web.Datasource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * UI-independent post-process pipeline: scan a folder (archives included), extract,
 * match, plan renames into an optional structured output tree, then apply with
 * journalling and archive cleanup. Mirrors {@link app.renami.cli.PostProcessCommand};
 * the CLI runs on this same controller.
 */
public final class PostProcessController {

    public record Options(String mode, String tvFormat, String movieFormat, String action,
                          ConflictPolicy conflict, double minScore, String order,
                          boolean clean, boolean companion) {
    }

    /** A planned run: what would happen, plus the archives and temp dirs gathered along the way. */
    public record Preview(List<RenameOperation> plan, List<Path> archives, List<Path> extractedDirs) {
    }

    private final HistoryStore history;

    public PostProcessController(HistoryStore history) {
        this.history = history;
    }

    /** Extracts archives and plans the rename batch. Report progress through {@code progress}. */
    public Preview plan(Path input, Path output, Options options, Datasource datasource,
                        Consumer<String> progress) throws Exception {
        List<Path> archives = findArchives(input);
        List<Path> candidates = new ArrayList<>(MediaScanner.scan(input));
        List<Path> extractedDirs = new ArrayList<>();
        for (Path archive : archives) {
            Path extractDir = input.resolve(".renami-extract").resolve(stripExtension(archive));
            try {
                ArchiveTool.extract(archive, extractDir);
                extractedDirs.add(extractDir);
                candidates.addAll(MediaScanner.scan(extractDir));
                progress.accept("extracted: " + archive.getFileName());
            } catch (IOException e) {
                progress.accept("skip archive " + archive.getFileName() + ": " + e.getMessage());
            }
        }
        if (candidates.isEmpty()) {
            cleanup(extractedDirs);
            throw new IllegalStateException("no media files found under " + input);
        }

        RenameService service = new RenameService(datasource, System.err);
        List<RenameService.Item> items = service.parseAll(candidates);
        boolean episodeMode = options.mode().equalsIgnoreCase("episode")
                || (options.mode().equalsIgnoreCase("auto") && items.stream()
                .anyMatch(i -> i.info().hasEpisode() || i.info().hasAbsoluteEpisode()));
        String template = episodeMode ? options.tvFormat() : options.movieFormat();
        RenameService.Options serviceOptions =
                new RenameService.Options(options.mode(), template, options.minScore(), options.order());
        // FileBot semantics: '/' in the format builds folders even when processing
        // in place — resolve the structure under the process folder itself.
        RenameService.TargetResolver resolver = output != null
                ? RenameService.structuredResolver(template, output.toAbsolutePath())
                : template.contains("/")
                ? RenameService.structuredResolver(template, input.toAbsolutePath())
                : null;
        List<RenameOperation> plan = service.plan(candidates, serviceOptions, resolver);
        if (options.companion()) {
            plan = CompanionFiles.expand(plan);
        }
        return new Preview(plan, archives, extractedDirs);
    }

    /** Executes the plan, journals it, optionally deletes processed archives, and tidies temp dirs. */
    public List<RenameExecutor.Result> apply(Preview preview, Options options,
                                             Consumer<RenameExecutor.Result> afterApply,
                                             Consumer<String> progress) throws IOException {
        TransferAction action = TransferAction.parse(options.action());
        RenameExecutor executor = new RenameExecutor(options.conflict(), action);
        List<RenameExecutor.Result> results = executor.apply(preview.plan());
        List<RenameOperation> applied = results.stream()
                .filter(RenameExecutor.Result::applied)
                .map(RenameExecutor.Result::operation)
                .toList();
        history.record(applied, action);
        if (afterApply != null) {
            results.forEach(afterApply);
        }
        if (options.clean()) {
            deleteArchives(preview.archives(), results, progress);
        }
        cleanup(preview.extractedDirs());
        return results;
    }

    /** Drops an unused preview (its extracted temp dirs). */
    public void discard(Preview preview) throws IOException {
        cleanup(preview.extractedDirs());
    }

    /** Only delete an archive when everything extracted from it was processed successfully. */
    private static void deleteArchives(List<Path> archives, List<RenameExecutor.Result> results,
                                       Consumer<String> progress) throws IOException {
        boolean allProcessed = results.stream().allMatch(r -> r.applied() || r.status().equals("unchanged"));
        if (!allProcessed) {
            progress.accept("--clean: keeping archives (not all contents processed)");
            return;
        }
        for (Path archive : archives) {
            Files.deleteIfExists(archive);
            progress.accept("[clean] deleted " + archive.getFileName());
        }
    }

    private static List<Path> findArchives(Path input) throws IOException {
        if (!Files.isDirectory(input)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(input)) {
            return stream.filter(ArchiveTool::isArchive).toList();
        }
    }

    private static void cleanup(List<Path> dirs) throws IOException {
        for (Path dir : dirs) {
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
        Path parent = dirs.isEmpty() ? null : dirs.get(0).getParent();
        if (parent != null) {
            Files.deleteIfExists(parent); // .renami-extract
        }
    }

    private static String stripExtension(Path archive) {
        String name = archive.getFileName().toString();
        for (String compound : List.of(".tar.gz", ".tar.bz2", ".tar.xz")) {
            if (name.toLowerCase().endsWith(compound)) {
                return name.substring(0, name.length() - compound.length());
            }
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
