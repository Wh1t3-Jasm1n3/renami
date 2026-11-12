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
    private final EpisodesPanel episodesPanel = new EpisodesPanel(renamePanel, () -> showView("rename"));
    private final PostProcessPanel postProcessPanel = new PostProcessPanel();
    private final WatchPanel watchPanel = new WatchPanel();
    private final SubtitlesPanel subtitlesPanel = new SubtitlesPanel();
    private final FilterPanel filterPanel = new FilterPanel(renamePanel, () -> showView("rename"));
    private final DupePanel dupePanel = new DupePanel();
    private final ChecksumPanel checksumPanel = new ChecksumPanel();
    private final HistoryPanel historyPanel = new HistoryPanel();
    private final ListPanel listPanel = new ListPanel();
    private final java.util.Map<String, JButton> primaryButtons = new java.util.HashMap<>();
    private JPanel content;
    private Sidebar sidebar;

    /** Switches the visible card and re-arms the Return-key default button. */
    void showView(String id) {
        ((CardLayout) content.getLayout()).show(content, id);
        if (sidebar != null) {
            sidebar.setActive(id);
        }
        JButton primary = primaryButtons.get(id);
        if (primary != null) {
            getRootPane().setDefaultButton(primary);
        }
    }

    /** FileBot's cross-mode transfer: files dropped on a rail mode switch and pipe in. */
    private void transferFiles(String id, List<java.nio.file.Path> files) {
        showView(id);
        switch (id) {
            case "rename" -> renamePanel.preload(files);
            case "checksum" -> checksumPanel.acceptFiles(files);
            case "subtitles" -> subtitlesPanel.acceptFiles(files);
            case "postprocess" -> postProcessPanel.preload(files);
            case "duplicates" -> dupePanel.acceptFiles(files);
            case "list" -> listPanel.acceptFiles(files);
            default -> { }
        }
    }

    public RenamiFrame() {
        this(List.of());
    }

    public RenamiFrame(java.util.List<java.nio.file.Path> preload) {
        super("Renami");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        AppIcon.applyTo(this);

        JPanel content = new JPanel(new CardLayout());
        this.content = content;
        content.add(renamePanel, "rename");
        content.add(episodesPanel, "episodes");
        content.add(postProcessPanel, "postprocess");
        content.add(watchPanel, "watch");
        content.add(subtitlesPanel, "subtitles");
        content.add(filterPanel, "filter");
        content.add(dupePanel, "duplicates");
        content.add(checksumPanel, "checksum");
        content.add(historyPanel, "history");
        content.add(listPanel, "list");

        // each view's primary action becomes the Return-key default button
        primaryButtons.putAll(java.util.Map.of(
                "rename", renamePanel.renameButton(),
                "episodes", episodesPanel.searchButton(),
                "postprocess", postProcessPanel.runButton(),
                "watch", watchPanel.startButton(),
                "subtitles", subtitlesPanel.downloadButton(),
                "filter", filterPanel.scanButton(),
                "duplicates", dupePanel.findButton(),
                "checksum", checksumPanel.generateButton(),
                "history", historyPanel.undoButton(),
                "list", listPanel.runButton()));

        // FileBot's rail set (Rename/Episodes/Subtitles/SFV/Filter/List) stays on the
        // rail; History is a toolbar dialog in FileBot, so it lives behind "More" here
        sidebar = new Sidebar(
                List.of(new Sidebar.Item("rename", "Rename", "rename", "#0A84FF"),
                        new Sidebar.Item("episodes", "Episodes", "episodes", "#BF5AF2"),
                        new Sidebar.Item("subtitles", "Subtitles", "subtitles", "#5E5CE6"),
                        new Sidebar.Item("checksum", "SFV", "checksum", "#30D158"),
                        new Sidebar.Item("filter", "Filter", "filter", "#FF9F0A"),
                        new Sidebar.Item("list", "List", "list", "#64D2FF")),
                List.of(new Sidebar.Item("postprocess", "Post-Process", "postprocess", "#FF6B6B"),
                        new Sidebar.Item("watch", "Watch", "watch", "#32D74B"),
                        new Sidebar.Item("duplicates", "Duplicates", "duplicates", "#FFB340"),
                        new Sidebar.Item("history", "History", "history", "#64D2FF")),
                this::showView,
                this::transferFiles);

        // FileBot's Post Process option: renamed files flow straight into the Post-Process view
        renamePanel.setPostProcessHandler(files -> {
            postProcessPanel.preload(files);
            showView("postprocess");
        });

        add(sidebar, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        getRootPane().setDefaultButton(renamePanel.renameButton());
        installMenuBar();

        String startView = System.getProperty("renami.view");
        if (startView != null && primaryButtons.containsKey(startView)) {
            showView(startView);
        }

        setSize(1120, 700);
        setLocationByPlatform(true);
    }

    /** FileBot's macOS menu bar: Help menu with the docs/support links. */
    private void installMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu help = new JMenu("Help");
        JMenuItem keys = new JMenuItem("API Keys…");
        keys.addActionListener(e -> ApiKeysDialog.show(this));
        help.add(keys);
        help.addSeparator();
        help.add(menuLink("Getting Started", "https://github.com/Wh1t3-Jasm1n3/renami"));
        help.add(menuLink("FAQ", "https://github.com/Wh1t3-Jasm1n3/renami#faq"));
        help.add(menuLink("Report Bugs", "https://github.com/Wh1t3-Jasm1n3/renami/issues"));
        help.add(menuLink("Request Help", "https://github.com/Wh1t3-Jasm1n3/renami/discussions"));
        help.addSeparator();
        help.add(menuLink("Check for Updates", "https://github.com/Wh1t3-Jasm1n3/renami/releases"));
        bar.add(help);
        setJMenuBar(bar);
    }

    private static JMenuItem menuLink(String text, String url) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> openUrl(url));
        return item;
    }

    private static void openUrl(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Could not open " + url + "\n" + ex.getMessage(),
                    "Browser", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** macOS app-menu About box. */
    static void showAbout() {
        String version = RenamiFrame.class.getPackage().getImplementationVersion();
        StringBuilder text = new StringBuilder("<html><body style='width:280px'>"
                + "<b>Renami</b> " + (version == null ? "dev" : version)
                + "<br>Open-source media file renamer and organizer.<br><br>"
                + "License: GPL-3.0 · No telemetry · Your files never leave your Mac."
                + "<br><br><a href='https://github.com/Wh1t3-Jasm1n3/renami'>"
                + "github.com/Wh1t3-Jasm1n3/renami</a></body></html>");
        JOptionPane.showMessageDialog(null, text, "About Renami", JOptionPane.PLAIN_MESSAGE);
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
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.eawt.quitStrategy", "CLOSE_ALL_WINDOWS");
        com.formdev.flatlaf.extras.FlatDesktop.setAboutHandler(RenamiFrame::showAbout);
        String theme = app.renami.util.Config.load().get("theme");
        RenamiTheme.apply(theme == null ? "auto" : theme);
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
            ApiKeysDialog.setupIfUnconfigured(frame);
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
