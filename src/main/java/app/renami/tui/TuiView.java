package app.renami.tui;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;

/**
 * One tab of the full-screen TUI. The app draws the tab bar and status line;
 * each view owns the content area between them.
 */
public interface TuiView {

    /** Short label shown in the tab bar. */
    String title();

    /** Draw into the content area (row 1..height-2 on the 80x24 floor). */
    void render(TextGraphics g, int width, int height);

    /** Content-area key handling; return true when consumed (blocks app-level keys). */
    boolean onKey(KeyStroke key);

    /** One-line help/result text for the bottom status bar. */
    String status();

    /** True while a background task runs; the app then repaints periodically. */
    default boolean busy() {
        return false;
    }

    /** Called when the tab becomes active (e.g. to refresh lists). */
    default void onActivate() {
    }
}
