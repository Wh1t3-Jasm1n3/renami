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
    private final JLabel viewHeader = Ui.header(" ");

    /** The window-level view title (centered over the whole window, sidebar included). */
    private static final java.util.Map<String, String> VIEW_TITLES = java.util.Map.of(
            "rename", "Rename", "episodes", "Episodes", "postprocess", "Post-Process",
            "watch", "Watch", "subtitles", "Subtitles", "filter", "Filter",
            "duplicates", "Duplicates", "checksum", "Checksum", "history", "History",
            "list", "List");

    /** Switches the visible card and re-arms the Return-key default button. */
    void showView(String id) {
        ((CardLayout) content.getLayout()).show(content, id);
        if (sidebar != null) {
            sidebar.setActive(id);
        }
        viewHeader.setText(VIEW_TITLES.getOrDefault(id, ""));
        JButton primary = primaryButtons.get(id);
        if (primary != null) {
            getRootPane().setDefaultButton(primary);
        }
    }

    /** Cross-mode transfer: files dropped on a rail mode switch and pipe in. */
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

        // The classic rail set (Rename/Episodes/Subtitles/SFV/Filter/List) stays on the
        // rail; History follows the toolbar-dialog pattern, so it lives behind "More" here
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

        // Post Process option: renamed files flow straight into the Post-Process view
        renamePanel.setPostProcessHandler(files -> {
            postProcessPanel.preload(files);
            showView("postprocess");
        });

        add(sidebar, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);

        // One header for every view, centered over the whole window — the per-panel
        // titles used to sit centered in the content area only, which read off-centre.
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setOpaque(false);
        headerBar.setBorder(BorderFactory.createEmptyBorder(16, 0, 2, 0));
        headerBar.add(viewHeader, BorderLayout.CENTER);
        add(headerBar, BorderLayout.NORTH);
        getRootPane().setDefaultButton(renamePanel.renameButton());
        installMenuBar();

        String startView = System.getProperty("renami.view");
        if (startView != null && primaryButtons.containsKey(startView)) {
            showView(startView);
        }

        setSize(1120, 700);
        enforceMinimumSize();
        setLocationByPlatform(true);
    }

    /**
     * Hold the window at the smallest size where every view shows all of its
     * controls: the widest view's preferred width plus the rail, capped at the
     * screen. Shrinking below that used to clip buttons off the bottom bars.
     */
    private void enforceMinimumSize() {
        int widest = content.getPreferredSize().width;
        for (Component c : content.getComponents()) {
            widest = Math.max(widest, c.getPreferredSize().width);
        }
        Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int minWidth = Math.min(widest + sidebar.getPreferredSize().width + 24, work.width - 40);
        int minHeight = Math.min(640, work.height - 120);
        setMinimumSize(new Dimension(minWidth, minHeight));
        if (getWidth() < minWidth || getHeight() < minHeight) {
            setSize(Math.max(getWidth(), minWidth), Math.max(getHeight(), minHeight));
        }
    }

    /** macOS menu bar: Help menu with the docs/support links. */
    private void installMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu help = new JMenu("Help");
        JMenuItem setup = new JMenuItem("Set Up Renami…");
        setup.addActionListener(e -> ApiKeysDialog.show(this));
        help.add(setup);
        help.addSeparator();
        help.add(menuLink("Getting Started", "https://github.com/Wh1t3-Jasm1n3/renami"));
        help.add(menuLink("FAQ", "https://github.com/Wh1t3-Jasm1n3/renami#faq"));
        help.add(menuLink("Report Bugs", "https://github.com/Wh1t3-Jasm1n3/renami/issues"));
        help.add(menuLink("Request Help", "https://github.com/Wh1t3-Jasm1n3/renami/discussions"));
        help.addSeparator();
        JMenuItem updates = new JMenuItem("Check for Updates…");
        updates.addActionListener(e -> UpdateDialog.checkInteractively(this));
        help.add(updates);
        bar.add(help);
        setJMenuBar(bar);
    }

    private static JMenuItem menuLink(String text, String url) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> openUrl(url));
        return item;
    }

    static void openUrl(String url) {
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
            ApiKeysDialog.showIfUnconfigured(frame);
            // Startup version check (Help ▸ Check for Updates does the manual one)
            UpdateDialog.checkOnStartup(frame);
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
