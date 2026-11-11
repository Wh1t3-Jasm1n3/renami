package app.renami.gui;

/**
 * Compatibility entry point: the look-and-feel now lives in {@link RenamiTheme}.
 * Kept so existing callers and tests keep working.
 */
public final class GuiTheme {

    private GuiTheme() {
    }

    /** Installs the look-and-feel. Call before creating any Swing component. */
    public static void apply() {
        RenamiTheme.apply();
    }

    /** @return true when the dark theme is active. */
    public static boolean isDark() {
        return RenamiTheme.isDark();
    }
}
