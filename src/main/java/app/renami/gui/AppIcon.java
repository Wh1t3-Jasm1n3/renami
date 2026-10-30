package app.renami.gui;

import javax.imageio.ImageIO;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * The application icon, rendered at build time by tools/GenerateIcon.java and
 * shipped inside the jar as a classpath resource (see the copyIconResource task).
 * A missing or unreadable icon never breaks the UI — it just falls back to the
 * platform default.
 */
public final class AppIcon {

    static final String RESOURCE = "/app/renami/app-icon.png";

    private AppIcon() {
    }

    /** @return the icon image, or null when it is not available. */
    public static BufferedImage load() {
        try (var in = AppIcon.class.getResourceAsStream(RESOURCE)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Applies the icon to a window and, where supported, the desktop Dock/taskbar. */
    public static void applyTo(Window window) {
        BufferedImage image = load();
        if (image == null) {
            return;
        }
        window.setIconImage(image);
        try {
            window.setIconImages(List.of(image));
        } catch (RuntimeException ignored) {
            // setIconImages is optional; setIconImage above already applied a single size
        }
        try {
            Taskbar.getTaskbar().setIconImage(image);
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // no taskbar support (other platforms; HeadlessException is an UnsupportedOperationException)
        }
    }
}
