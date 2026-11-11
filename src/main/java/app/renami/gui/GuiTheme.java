package app.renami.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.SystemInfo;

import javax.swing.UIManager;
import java.util.concurrent.TimeUnit;

/**
 * GUI look-and-feel: FlatLaf, following the macOS light/dark setting
 * (dark theme on a dark desktop, light on a light one). Falls back to the
 * platform L&F if FlatLaf cannot load, so the GUI still works on exotic setups.
 */
public final class GuiTheme {

    private GuiTheme() {
    }

    /** Installs the look-and-feel. Call before creating any Swing component. */
    public static void apply() {
        try {
            if (isSystemDark()) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Throwable ignored) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignoredAgain) {
                // stay on the cross-platform default
            }
        }
    }

    /** @return true when the dark FlatLaf theme is active. */
    public static boolean isDark() {
        return FlatLaf.isLafDark();
    }

    /** macOS stores the desktop appearance in one defaults key; absent means light. */
    static boolean isSystemDark() {
        if (!SystemInfo.isMacOS) {
            return false;
        }
        try {
            Process p = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start();
            String value = new String(p.getInputStream().readAllBytes()).trim();
            return p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0 && value.equalsIgnoreCase("Dark");
        } catch (Exception e) {
            return false;
        }
    }
}
