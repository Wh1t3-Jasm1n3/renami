package app.renami.gui;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Main application window for the Renami desktop UI. */
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

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Rename", renamePanel);
        tabs.addTab("Subtitles", new SubtitlesPanel());
        tabs.addTab("Duplicates", new DupePanel());
        tabs.addTab("History", new HistoryPanel());
        add(tabs, BorderLayout.CENTER);

        JLabel footer = new JLabel("  Renami — open-source media renamer. Undo anytime with `renami undo`.");
        add(footer, BorderLayout.SOUTH);

        setSize(900, 600);
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
        GuiTheme.apply();
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
