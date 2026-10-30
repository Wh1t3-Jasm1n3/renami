package app.renami.rename;

import java.util.Locale;

/**
 * How a file reaches its target: move it out of the way, or leave the original
 * in place and create a second reference to the same content.
 */
public enum TransferAction {

    /** Rename in place (the default). */
    RENAME("renamed", true),
    /** Move to another folder — same operation, different intent. */
    MOVE("moved", true),
    /** Byte-for-byte copy; the source stays where it is. */
    COPY("copied", false),
    /** Second directory entry for the same inode; needs no extra disk space. */
    HARDLINK("hardlinked", false),
    /** Pointer to the source path; works across filesystems. */
    SYMLINK("symlinked", false);

    public static final String CHOICES = "rename | move | copy | hardlink | symlink";

    private final String verb;
    private final boolean consumesSource;

    TransferAction(String verb, boolean consumesSource) {
        this.verb = verb;
        this.consumesSource = consumesSource;
    }

    /** Past-tense status word for CLI/GUI output and the history journal. */
    public String verb() {
        return verb;
    }

    /** True when the source is gone afterwards, so undo must move the file back. */
    public boolean consumesSource() {
        return consumesSource;
    }

    public static TransferAction parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("unknown action \"" + value + "\" (use " + CHOICES + ")");
        }
    }
}
