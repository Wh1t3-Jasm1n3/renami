package app.renami.rename;

public enum ConflictPolicy {
    /** Leave the existing target file untouched and skip this operation. */
    SKIP,
    /** Replace the existing target file. */
    OVERWRITE,
    /** Append " (2)", " (3)", … until the name is free. */
    INDEX,
    /** Do nothing and report the conflict (default). */
    FAIL
}
