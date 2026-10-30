package app.renami.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Archive access by shelling out to native tools (unzip / tar / 7z),
 * mirroring the "ShellExecutables" strategy used by mature media tools.
 */
public final class ArchiveTool {

    public static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
            "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "iso");

    public enum Kind {UNZIP, TAR, SEVEN_ZIP}

    public record Backend(Kind kind, String executable) {
    }

    private ArchiveTool() {
    }

    public static boolean isArchive(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".tar.gz") || name.endsWith(".tar.bz2") || name.endsWith(".tar.xz")) {
            return true;
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && ARCHIVE_EXTENSIONS.contains(name.substring(dot + 1));
    }

    /** @return a usable backend for this archive type, or null when no tool is installed */
    public static Backend detect(Path archive) {
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        boolean tarLike = ext.equals("tar") || ext.equals("gz") || ext.equals("bz2")
                || ext.equals("xz") || ext.equals("tgz") || ext.equals("iso");
        if (ext.equals("zip")) {
            if (hasTool("unzip")) {
                return new Backend(Kind.UNZIP, "unzip");
            }
            if (hasTool("tar")) {
                return new Backend(Kind.TAR, "tar");
            }
        }
        if (tarLike && hasTool("tar")) {
            return new Backend(Kind.TAR, "tar");
        }
        for (String tool : List.of("7zz", "7z", "7za")) {
            if (hasTool(tool)) {
                return new Backend(Kind.SEVEN_ZIP, tool);
            }
        }
        return null;
    }

    public static List<String> list(Path archive) throws IOException {
        Backend backend = requireBackend(archive);
        List<String> cmd = switch (backend.kind()) {
            case UNZIP -> List.of(backend.executable(), "-Z1", abs(archive));
            case TAR -> List.of(backend.executable(), "-tf", abs(archive));
            case SEVEN_ZIP -> List.of(backend.executable(), "l", "-slt", abs(archive));
        };
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes());
        await(process, archive);
        List<String> entries = new ArrayList<>();
        for (String line : out.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Path = ")) { // 7z -slt format
                String path = trimmed.substring("Path = ".length());
                if (!path.equals(archive.getFileName().toString())) {
                    entries.add(path);
                }
            } else if (!trimmed.isEmpty() && !trimmed.startsWith("Archive:") && !trimmed.startsWith("Type =")) {
                entries.add(trimmed);
            }
        }
        return entries;
    }

    public static void extract(Path archive, Path outDir) throws IOException {
        Backend backend = requireBackend(archive);
        Files.createDirectories(outDir);
        List<String> cmd = switch (backend.kind()) {
            case UNZIP -> List.of(backend.executable(), "-o", "-q", abs(archive), "-d", abs(outDir));
            case TAR -> List.of(backend.executable(), "-xf", abs(archive), "-C", abs(outDir));
            // 7z syntax: -oOUT with no space
            case SEVEN_ZIP -> List.of(backend.executable(), "x", "-y", "-o" + abs(outDir), abs(archive));
        };
        Process process = new ProcessBuilder(cmd)
                .directory(archive.toAbsolutePath().getParent().toFile())
                .redirectErrorStream(true)
                .start();
        String out = new String(process.getInputStream().readAllBytes());
        int code = await(process, archive);
        if (code != 0) {
            throw new IOException("extraction failed (" + backend.executable() + " exit " + code + "): " + out);
        }
    }

    private static String abs(Path p) {
        return p.toAbsolutePath().toString();
    }

    private static Backend requireBackend(Path archive) throws IOException {
        Backend backend = detect(archive);
        if (backend == null) {
            throw new IOException("no tool available for " + archive.getFileName()
                    + " (install p7zip via `brew install p7zip` for 7z/rar support)");
        }
        return backend;
    }

    private static boolean hasTool(String name) {
        try {
            Process p = new ProcessBuilder(name).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor(5, TimeUnit.SECONDS);
            p.destroy();
            return true; // any tool that starts (even with a usage error) exists
        } catch (Exception e) {
            return false;
        }
    }

    private static int await(Process process, Path archive) throws IOException {
        try {
            if (!process.waitFor(300, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("archive tool timed out on " + archive);
            }
            return process.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
