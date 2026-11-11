package app.renami.gui;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Main application window: a macOS-style source-list sidebar on the left and
 * one view per sidebar entry in the content area.
 */
public final class RenamiFrame extends JFrame {

    private final RenamePanel renamePanel = new RenamePanel();

    public RenamiFrame() {
        this(List.of());
    }

    public RenamiFrame(java.util.List<java.nio.file.Path> preload) {
        super("Renami");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        AppIcon.applyTo(this);

        JPanel content = new JPanel(new CardLayout());
        content.add(renamePanel, "rename");
        content.add(new SubtitlesPanel(), "subtitles");
        content.add(new DupePanel(), "duplicates");
        content.add(new HistoryPanel(), "history");

        Sidebar sidebar = new Sidebar(
                List.of(new Sidebar.Item("rename", "Rename", "rename"),
                        new Sidebar.Item("subtitles", "Subtitles", "subtitles"),
                        new Sidebar.Item("duplicates", "Duplicates", "duplicates"),
                        new Sidebar.Item("history", "History", "history")),
                id -> ((CardLayout) content.getLayout()).show(content, id));

        add(sidebar, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        getRootPane().setDefaultButton(renamePanel.renameButton());

        setSize(1000, 640);
        setLocationByPlatform(true);
    }

    /** Opens the desktop UI and blocks until the last window is closed. */
    public static int open() {
        return open(List.of());
    }

    /** Opens the desktop UI with files preloaded into the rename panel (Finder open-with). */
    public static int open(java.util.List<java.nio.file.Path> preload) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("error: no display available — use the `rename` command instead");
            return 3;
        }
        RenamiTheme.apply();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            RenamiFrame frame = new RenamiFrame(preload);
            if (!preload.isEmpty()) {
                frame.renamePanel.preload(preload);
            }
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    latch.countDown();
                }
            });
            frame.setVisible(true);
        });
        try {
            latch.await();
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        }
    }
}
