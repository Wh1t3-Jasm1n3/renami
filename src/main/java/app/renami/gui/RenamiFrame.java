package app.renami.gui;

import javax.swing.*;
import java.awt.*;

/** Main application window for the Renami desktop UI. */
public final class RenamiFrame extends JFrame {

    public RenamiFrame() {
        super("Renami");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Rename", new RenamePanel());
        add(tabs, BorderLayout.CENTER);

        JLabel footer = new JLabel("  Renami — open-source media renamer. Undo anytime with `renami undo`.");
        add(footer, BorderLayout.SOUTH);

        setSize(900, 600);
        setLocationByPlatform(true);
    }

    /** Opens the desktop UI and blocks until the last window is closed. */
    public static int open() {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("error: no display available — use the `rename` command instead");
            return 3;
        }
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            RenamiFrame frame = new RenamiFrame();
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
