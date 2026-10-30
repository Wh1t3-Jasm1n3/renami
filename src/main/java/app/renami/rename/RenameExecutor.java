package app.renami.rename;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Applies rename operations with conflict handling, case-safety and transfer mode. */
public final class RenameExecutor {

    public record Result(RenameOperation operation, String status, Path actualTarget) {

        /** True when the transfer actually happened, so it can be journalled and undone. */
        public boolean applied() {
            return status.equals("renamed") || status.equals("moved") || status.equals("copied")
                    || status.equals("hardlinked") || status.equals("symlinked")
                    || status.startsWith("copied (");
        }
    }

    private final ConflictPolicy conflictPolicy;
    private final TransferAction action;

    public RenameExecutor(ConflictPolicy conflictPolicy, TransferAction action) {
        this.conflictPolicy = conflictPolicy;
        this.action = action;
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
            if (!action.consumesSource() && isSameFile(op.source(), target)) {
                // A case-only target on a case-insensitive filesystem would copy/hardlink a file
                // onto itself and destroy data; refuse instead.
                results.add(new Result(op, "skipped (same file)", target));
                continue;
            }
            results.add(new Result(op, transfer(op.source(), target), target));
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

    /** @return the status word to report (the action verb, or a fallback note when the FS refused it) */
    private String transfer(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        return switch (action) {
            case RENAME, MOVE -> {
                relocate(source, target);
                yield action.verb();
            }
            case COPY -> {
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                yield action.verb();
            }
            case HARDLINK -> {
                try {
                    unlinkExistingTarget(target);
                    Files.createLink(target, source);
                    yield action.verb();
                } catch (FileSystemException | UnsupportedOperationException e) {
                    // Cross-device, or a filesystem without hardlink support: degrade to a copy so
                    // the batch still completes, and say so.
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                    yield "copied (hardlink unsupported: " + e.getMessage() + ")";
                }
            }
            case SYMLINK -> {
                try {
                    unlinkExistingTarget(target);
                    Files.createSymbolicLink(target, source.toAbsolutePath());
                    yield action.verb();
                } catch (FileSystemException | UnsupportedOperationException e) {
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                    yield "copied (symlink unsupported: " + e.getMessage() + ")";
                }
            }
        };
    }

    private void relocate(Path source, Path target) throws IOException {
        boolean samePath = source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize());
        if (samePath) {
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

    /** OVERWRITE conflicts must remove the old entry before link creation, which never replaces. */
    private void unlinkExistingTarget(Path target) throws IOException {
        if (conflictPolicy == ConflictPolicy.OVERWRITE && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(target);
        }
    }

    private static boolean isSameFile(Path a, Path b) {
        try {
            return Files.isSameFile(a, b);
        } catch (IOException e) {
            return false;
        }
    }
}
