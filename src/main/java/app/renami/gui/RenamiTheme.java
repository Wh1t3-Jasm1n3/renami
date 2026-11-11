package app.renami.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.SystemInfo;

import javax.swing.UIManager;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Look-and-feel: FlatLaf restyled toward the 2026 macOS design language —
 * the SF Pro system font, the macOS accent colour, rounded controls with a
 * focus ring, and roomy grid-less tables. Follows the desktop light/dark
 * appearance. Falls back to the platform L&F if FlatLaf cannot load.
 */
public final class RenamiTheme {

    /** SF Pro is Apple's system font; load it straight from the system folder. */
    private static final File SF_PRO = new File("/System/Library/Fonts/SFNS.ttf");

    /**
     * SFNS.ttf is a variable font whose advance widths Java2D under-reports,
     * so tight label layouts clip their last glyph ("Original Files" -> "Original File").
     * Helvetica Neue renders nearly identical on macOS and reports safe widths.
     */
    private static final String SAFE_UI_FONT = "Helvetica Neue";

    private RenamiTheme() {
    }

    /** Installs the look-and-feel (following the desktop appearance). Call before creating any Swing component. */
    public static void apply() {
        apply("auto");
    }

    /** Installs the look-and-feel for an explicit mode: auto | light | dark. */
    public static void apply(String mode) {
        boolean dark = switch (mode == null ? "auto" : mode.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "dark" -> true;
            case "light" -> false;
            default -> isSystemDark();
        };
        Font uiFont = systemUiFont();
        if (uiFont != null) {
            UIManager.put("defaultFont", uiFont);
        }
        FlatLaf.setGlobalExtraDefaults(extras(dark));
        try {
            if (dark) {
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

    /**
     * Switch the theme at runtime: persists the choice to ~/.renami/config.properties,
     * re-installs the look-and-feel and refreshes every open window.
     *
     * @param mode auto | light | dark
     */
    public static void setTheme(String mode) {
        app.renami.util.Config.set("theme", mode);
        apply(mode);
        for (java.awt.Frame f : java.awt.Frame.getFrames()) {
            javax.swing.SwingUtilities.updateComponentTreeUI(f);
            f.invalidate();
        }
    }

    /** @return true when the dark FlatLaf theme is active. */
    public static boolean isDark() {
        return FlatLaf.isLafDark();
    }

    /** @return the UI font at 13pt, or null when no safe font is available. */
    static Font systemUiFont() {
        try {
            java.util.Set<String> families = java.util.Arrays
                    .stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                    .collect(java.util.stream.Collectors.toSet());
            if (families.contains(SAFE_UI_FONT)) {
                return new Font(SAFE_UI_FONT, Font.PLAIN, 13);
            }
            if (SF_PRO.isFile()) {
                Font base = Font.createFont(Font.TRUETYPE_FONT, SF_PRO);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
                return base.deriveFont(Font.PLAIN, 13f);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    /** FlatLaf theme overrides: rounded geometry, accent, flat tables, slim scrollbars. */
    static Map<String, String> extras(boolean dark) {
        Map<String, String> m = new HashMap<>();
        // macOS blue as the accent base; a user-picked system accent still wins
        m.put("@accentBaseColor", dark ? "#0A84FF" : "#0071E3");

        // rounded controls with a macOS-style focus ring
        m.put("Button.arc", "10");
        m.put("Component.arc", "8");
        m.put("TextComponent.arc", "8");
        m.put("Component.borderWidth", "1");
        m.put("Component.focusWidth", "3");
        m.put("Component.innerFocusWidth", "0");
        m.put("Component.arrowType", "chevron");

        // roomy, grid-less tables with a quiet header
        m.put("Table.rowHeight", "30");
        m.put("Table.showGrid", "false");
        m.put("Table.showHorizontalLines", "false");
        m.put("Table.showVerticalLines", "false");
        m.put("Table.alternateRowColor", dark ? "#414547" : "#F6F6F9");
        m.put("Table.header.height", "26");
        m.put("Table.header.bottomSeparatorColor", dark ? "#4B4F51" : "#E2E2E8");
        m.put("Table.selectionInactiveBackground", dark ? "#4A4E50" : "#E4E4EA");

        // comfortable list rows (sidebar)
        m.put("List.cellMargins", "5,10,5,10");

        // slim overlay-ish scrollbars
        m.put("ScrollBar.width", "12");
        m.put("ScrollBar.showButtons", "false");

        // roomy menus with rounded corners
        m.put("MenuItem.heightScale", "1.2");
        m.put("PopupMenu.borderCornerRadius", "8");

        m.put("Separator.color", dark ? "#4B4F51" : "#E0E0E6");
        return m;
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
