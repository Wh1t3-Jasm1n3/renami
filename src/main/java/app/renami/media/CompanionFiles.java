package app.renami.media;

import app.renami.archive.ArchiveTool;
import app.renami.rename.RenameOperation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Companion files: sidecars that share a media file's base name — subtitles
 * ({@code Movie.en.srt}), metadata ({@code Movie.nfo}), artwork ({@code Movie.jpg}).
 * When the media file is renamed or moved they should follow it, mirroring the
 * companion-import step of the classic post-process pipelines.
 */
public final class CompanionFiles {

    private CompanionFiles() {
    }

    /** All companions of {@code mediaFile} in its own folder, sorted for deterministic plans. */
    public static List<Path> find(Path mediaFile) throws IOException {
        Path parent = mediaFile.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return List.of();
        }
        String base = baseName(mediaFile.getFileName().toString());
        try (var entries = Files.list(parent)) {
            List<Path> result = new ArrayList<>();
            entries.filter(Files::isRegularFile)
                    .filter(p -> !isSameFile(p, mediaFile))
                    .filter(p -> isCompanionOf(base, p))
                    .sorted()
                    .forEach(result::add);
            return result;
        }
    }

    /**
     * Grows a media plan so every transferred file drags its companions along: they land next to
     * the media target, reusing the media target's base name and keeping their own extension.
     * Files already in the plan (e.g. a second video with the same base) are never duplicated.
     */
    public static List<RenameOperation> expand(List<RenameOperation> mediaPlan) throws IOException {
        List<RenameOperation> expanded = new ArrayList<>();
        Set<Path> planned = new HashSet<>();
        for (RenameOperation op : mediaPlan) {
            planned.add(op.source());
        }
        for (RenameOperation op : mediaPlan) {
            expanded.add(op);
            String targetBase = baseName(op.target().getFileName().toString());
            String mediaBase = baseName(op.source().getFileName().toString());
            for (Path companion : find(op.source())) {
                if (!planned.add(companion)) {
                    continue;
                }
                String ext = MediaScanner.extension(companion);
                String name = targetBase + languageTag(mediaBase, companion)
                        + (ext.isEmpty() ? "" : "." + ext);
                expanded.add(new RenameOperation(companion, op.target().resolveSibling(name)));
            }
        }
        return expanded;
    }

    private static boolean isCompanionOf(String mediaBase, Path candidate) {
        String name = candidate.getFileName().toString();
        if (name.startsWith(".") || name.endsWith(".tmp") || name.endsWith(".part")) {
            return false; // hidden files and partial downloads are nobody's sidecar
        }
        if (MediaScanner.isMedia(candidate) || ArchiveTool.isArchive(candidate)) {
            return false; // scanned and planned on their own
        }
        String candBase = baseName(name);
        if (candBase.equalsIgnoreCase(mediaBase)) {
            return true;
        }
        // Language-tagged subtitles: Movie.en.srt / Movie.eng.sub follow Movie.mkv.
        int dot = candBase.lastIndexOf('.');
        if (dot <= 0 || !candBase.substring(0, dot).equalsIgnoreCase(mediaBase)) {
            return false;
        }
        String tag = candBase.substring(dot + 1).toLowerCase(Locale.ROOT);
        return tag.length() >= 2 && tag.length() <= 3 && tag.chars().allMatch(Character::isLetter);
    }

    /** Keeps the {@code .en} in {@code Movie.en.srt} so subtitle languages survive the rename. */
    private static String languageTag(String mediaBase, Path companion) {
        String candBase = baseName(companion.getFileName().toString());
        int dot = candBase.lastIndexOf('.');
        if (dot > 0 && candBase.substring(0, dot).equalsIgnoreCase(mediaBase)) {
            return "." + candBase.substring(dot + 1);
        }
        return "";
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private static boolean isSameFile(Path a, Path b) {
        try {
            return Files.isSameFile(a, b);
        } catch (IOException e) {
            return false;
        }
    }
}
