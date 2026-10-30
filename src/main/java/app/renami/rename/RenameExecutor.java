package app.renami.rename;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Applies rename operations with conflict handling and case-safety. */
public final class RenameExecutor {

    public record Result(RenameOperation operation, String status, Path actualTarget) {
    }

    private final ConflictPolicy conflictPolicy;
    private final boolean hardlink;

    public RenameExecutor(ConflictPolicy conflictPolicy, boolean hardlink) {
        this.conflictPolicy = conflictPolicy;
        this.hardlink = hardlink;
    }

    public List<Result> apply(List<RenameOperation> operations) throws IOException {
        List<Result> results = new ArrayList<>();
        for (RenameOperation op : operations) {
            if (op.isNoOp()) {
                results.add(new Result(op, "unchanged", op.target()));
                continue;
            }
            Path target = resolveConflict(op.target());
            if (target == null) {
                results.add(new Result(op, "skipped (conflict)", op.target()));
                continue;
            }
            move(op.source(), target);
            results.add(new Result(op, hardlink ? "hardlinked" : "renamed", target));
        }
        return results;
    }

    /** @return a usable target path, or null when the operation should be skipped. */
    private Path resolveConflict(Path target) throws IOException {
        if (!exactNameExists(target)) {
            return target;
        }
        switch (conflictPolicy) {
            case OVERWRITE -> {
                return target;
            }
            case SKIP -> {
                return null;
            }
            case FAIL -> throw new IOException("Target exists: " + target);
            case INDEX -> {
                String name = target.getFileName().toString();
                int dot = name.lastIndexOf('.');
                String base = dot < 0 ? name : name.substring(0, dot);
                String ext = dot < 0 ? "" : name.substring(dot);
                for (int i = 2; i < 1000; i++) {
                    Path candidate = target.resolveSibling(base + " (" + i + ")" + ext);
                    if (!exactNameExists(candidate)) {
                        return candidate;
                    }
                }
                return null;
            }
        }
        return null;
    }

    /**
     * Case-sensitive existence check: on case-insensitive filesystems (APFS default)
     * Files.exists("Movie.mkv") is true for "movie.mkv", but a case-only rename is
     * not a conflict — it is the operation we are about to perform.
     */
    private static boolean exactNameExists(Path target) throws IOException {
        Path parent = target.getParent();
        String name = target.getFileName().toString();
        if (parent == null || !Files.isDirectory(parent)) {
            return false;
        }
        try (var entries = Files.list(parent)) {
            return entries.anyMatch(p -> p.getFileName().toString().equals(name));
        }
    }

    private void move(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        boolean samePath = source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize());
        if (samePath) {
            return;
        }
        if (hardlink) {
            Files.createLink(target, source);
            return;
        }
        // case-only rename on case-insensitive filesystems needs a two-phase move
        boolean caseOnly = source.getFileName().toString().equalsIgnoreCase(target.getFileName().toString())
                && source.getParent().toAbsolutePath().normalize().equals(target.getParent().toAbsolutePath().normalize());
        try {
            if (caseOnly) {
                Path temp = target.resolveSibling(target.getFileName().toString() + ".renami.tmp");
                Files.move(source, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
