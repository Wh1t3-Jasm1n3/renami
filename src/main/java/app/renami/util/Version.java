package app.renami.util;

/** Application version, read from the jar manifest with a development fallback. */
public final class Version {

    public static final String NAME = "Renami";

    private Version() {
    }

    public static String get() {
        String v = Version.class.getPackage().getImplementationVersion();
        return v != null && !v.isBlank() ? v : "0.0.0-dev";
    }

    public static String full() {
        return NAME + " " + get();
    }
}
