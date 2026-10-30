package app.renami.rename;

import java.nio.file.Path;

/** A single planned file move. */
public record RenameOperation(Path source, Path target) {

    public boolean isNoOp() {
        return source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize());
    }
}
