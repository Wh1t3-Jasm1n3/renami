package app.renami.gui;

import app.renami.format.FormatEngine;
import app.renami.media.MediaScanner;
import app.renami.pipeline.RenameService;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.TransferAction;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.Datasources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The rename view: the proven dual-panel layout — "Original Files" on the left,
 * "New Names" on the right, Match/Rename in the center column, each list with
 * its own tool strip — dressed in the 2026-macOS design language.
 *
 * One shared row model drives both lists, so the two panels can never drift
 * out of alignment (other renamers need a whole "Match/align" algorithm for that).
 */
public final class RenamePanel extends JPanel {

    /** One file pairing: original path, planned target, state, and the matched display name. */
    private record Pair(Path source, Path target, String status, String display) {

        Pair(Path source, Path target, String status) {
            this(source, target, status, null);
        }
    }

    private static final String STATUS_RENAME = "rename";
    private static final String STATUS_UNCHANGED = "unchanged";
    private static final String STATUS_NO_MATCH = "no match";

    // ---- options (Settings popup) ----

    private final SegmentedControl modeControl =
            new SegmentedControl("auto", "movie", "episode", "local");
    private final SegmentedControl actionControl =
            new SegmentedControl("rename", "move", "copy", "hardlink", "symlink");
    private final SegmentedControl conflictControl =
            new SegmentedControl("fail", "skip", "overwrite", "index");
    private final SegmentedControl extensionControl =
            new SegmentedControl("preserve", "override");
    private final JComboBox<String> dbBox =
            new JComboBox<>(new String[]{"auto", "tmdb", "tvdb", "tvmaze", "omdb", "kitsu", "local"});
    private final JSpinner minScoreSpinner =
            new JSpinner(new SpinnerNumberModel(0.60, 0.0, 1.0, 0.05));
    private final JCheckBox companionCheck = new JCheckBox("Rename companion files", true);
    private final JCheckBox postProcessCheck = new JCheckBox("Post Process", false);

    /** Set by RenamiFrame: hands freshly applied files to the Post-Process view. */
    private java.util.function.Consumer<java.util.List<Path>> postProcessHandler;

    public void setPostProcessHandler(java.util.function.Consumer<java.util.List<Path>> handler) {
        this.postProcessHandler = handler;
    }
    private String format = "{n} - {s00e00} - {t}";

    // ---- the shared row model behind both lists ----

    private final DefaultListModel<Pair> rows = new DefaultListModel<>();
    private final JList<Pair> originalList = new JList<>(rows);
    private final JList<Pair> namesList = new JList<>(rows);
    private final JScrollPane originalScroller = new JScrollPane(originalList);
    private final JScrollPane namesScroller = new JScrollPane(namesList);
    private final JPanel originalCard = new JPanel(new CardLayout());
    private final JPanel namesCard = new JPanel(new CardLayout());

    /** Format variables from the last Match, per source file (for Edit Format). */
    private Map<Path, Map<String, Object>> lastVars = Map.of();

    private final JButton matchButton = new JButton("Match");
    private final JButton renameButton = new JButton("Rename");
    private final JLabel statusLabel = new JLabel("Drop files or a folder here");

    private final HistoryStore history = new HistoryStore();

    public RenamePanel() {
        super(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(18, 22, 14, 22));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        JLabel title = Ui.header("Rename");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        titleBar.add(title, BorderLayout.CENTER);
        add(titleBar, BorderLayout.NORTH);

        JPanel panels = new JPanel(new GridBagLayout());
        panels.setOpaque(false);
        GridBagConstraints pc = new GridBagConstraints();
        pc.gridy = 0; pc.fill = GridBagConstraints.BOTH; pc.weighty = 1;
        pc.gridx = 0; pc.weightx = 1;
        panels.add(buildOriginalCard(), pc);
        pc.gridx = 1; pc.weightx = 0; pc.insets = new Insets(0, 14, 0, 14);
        panels.add(buildCenterColumn(), pc);
        pc.gridx = 2; pc.weightx = 1; pc.insets = new Insets(0, 0, 0, 0);
        panels.add(buildNamesCard(), pc);
        add(panels, BorderLayout.CENTER);

        add(buildBottomBar(), BorderLayout.SOUTH);

        // The Match button opens the Fetch & Match Data popup, it does not match directly
        matchButton.addActionListener(e ->
                buildFetchPopup().show(matchButton, 0, matchButton.getHeight()));
        renameButton.addActionListener(e -> rename());
        renameButton.setEnabled(false);
        // Right-click Rename → Settings popup
        matchButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    buildFetchPopup().show(matchButton, e.getX(), e.getY());
                }
            }
        });
        renameButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    buildSettingsPopup().show(renameButton, e.getX(), e.getY());
                }
            }
        });
        installPresetKeys();

        installListBehavior();
        updateEmptyState();
        FileDrop.register(this, this::addFiles);
    }

    /** The primary action, so the frame can make it the default (Return) button. */
    JButton renameButton() {
        return renameButton;
    }

    /** Preload files or a folder opened from the command line. */
    public void preload(List<Path> dropped) {
        addFiles(dropped);
        // GUI smoke-test hook: -Drenami.automatch=true runs Match right after preload
        if (Boolean.getBoolean("renami.automatch") && !rows.isEmpty()) {
            SwingUtilities.invokeLater(this::match);
        }
    }

    /** Replace the row model with explicit pairings (used by render checks and tests). */
    void setPairings(List<Path> sources, List<Path> targets, List<String> statuses) {
        rows.clear();
        for (int i = 0; i < sources.size(); i++) {
            rows.addElement(new Pair(sources.get(i), targets.get(i), statuses.get(i)));
        }
        updateEmptyState();
    }

    // ============================================================
    // the two panels
    // ============================================================

    private JComponent buildOriginalCard() {
        originalList.setCellRenderer(new PairRenderer(true));
        originalScroller.setBorder(BorderFactory.createEmptyBorder());
        originalCard.add(originalScroller, "list");
        originalCard.add(buildDropZone(), "empty");

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        toolbar.setOpaque(false);
        toolbar.add(iconButton("up", "Align Up", e -> moveSelected(-1)));
        toolbar.add(iconButton("down", "Align Down", e -> moveSelected(1)));
        toolbar.add(iconButton("exclude", "Exclude Selected Items (Delete)", e -> removeSelected()));
        toolbar.add(textButton("Load", e -> loadFiles()));
        toolbar.add(popupButton("clear", "Clear", this::buildClearPopup));
        toolbar.add(iconButton("history", "History", e -> showHistoryDialog()));
        toolbar.add(popupButton("presets", "Presets", this::buildPresetsPopup));
        return buildCard("Original Files", originalCard, toolbar);
    }

    private JComponent buildNamesCard() {
        namesList.setCellRenderer(new PairRenderer(false));
        namesScroller.setBorder(BorderFactory.createEmptyBorder());
        namesCard.add(namesScroller, "list");
        namesCard.add(buildNamesPlaceholder(), "empty");

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        toolbar.setOpaque(false);
        toolbar.add(iconButton("up", "Align Up", e -> moveSelected(-1)));
        toolbar.add(iconButton("down", "Align Down", e -> moveSelected(1)));
        toolbar.add(iconButton("match", "Match", e -> match()));
        // Fetch Data opens the Fetch & Match Data popup, it does not match directly
        JButton fetch = new JButton("Fetch Data");
        fetch.addActionListener(e -> buildFetchPopup().show(fetch, 0, -fetch.getHeight()));
        toolbar.add(fetch);
        toolbar.add(popupButton("paste", "Load Names", this::buildLoadNamesPopup));
        toolbar.add(popupButton("settings", "Settings", this::buildSettingsPopup));
        toolbar.add(popupButton("edit", "Edit Match", this::buildEditPopup));
        return buildCard("New Names", namesCard, toolbar);
    }

    /** Titled card: caption on top, list in the middle, tool strip at the bottom. */
    private JComponent buildCard(String title, JPanel card, JPanel toolbar) {
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        JPanel captionRow = new JPanel(new BorderLayout());
        captionRow.setOpaque(false);
        captionRow.add(Ui.caption(title), BorderLayout.WEST);
        body.add(captionRow, BorderLayout.NORTH);
        body.add(card, BorderLayout.CENTER);
        body.add(toolbar, BorderLayout.SOUTH);
        return Ui.card(body);
    }

    private JComponent buildCenterColumn() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.add(Box.createVerticalGlue());
        column.add(centerButton(matchButton, "match", 92, false));
        column.add(Box.createVerticalStrut(14));
        column.add(centerButton(renameButton, "rename", 92, true));
        column.add(Box.createVerticalGlue());
        return column;
    }

    /** Icon-over-text action button in the center column, our styling. */
    private static JComponent centerButton(JButton button, String icon, int size, boolean prominent) {
        button.setIcon(Ui.icon(icon, size - 34, prominent ? () -> Color.WHITE : Ui::text));
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.putClientProperty("JButton.buttonType", "square");
        button.putClientProperty("JComponent.sizeVariant", "large");
        button.setPreferredSize(new Dimension(size + 18, size));
        button.setMaximumSize(new Dimension(size + 18, size));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        return button;
    }

    private JComponent buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        statusLabel.setForeground(Ui.secondary());
        bar.add(statusLabel, BorderLayout.CENTER);
        return bar;
    }

    private static JComponent captioned(String caption, JComponent control) {
        JPanel field = new JPanel(new BorderLayout(0, 3));
        field.setOpaque(false);
        field.add(Ui.caption(caption), BorderLayout.NORTH);
        field.add(control, BorderLayout.CENTER);
        return field;
    }

    // ============================================================
    // list behavior: selection sync, context menus, keys, empty state
    // ============================================================

    private void installListBehavior() {
        for (JList<Pair> list : List.of(originalList, namesList)) {
            list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            list.setVisibleRowCount(-1);
            list.setFixedCellHeight(-1);
        }
        // selecting a row in either panel highlights the same pairing in both
        originalList.addListSelectionListener(e -> syncSelection(originalList, namesList));
        namesList.addListSelectionListener(e -> syncSelection(namesList, originalList));

        MouseAdapter openMenu = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShow(e);
            }

            private void maybeShow(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JList<Pair> list = (JList<Pair>) e.getComponent();
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0 && !list.isSelectedIndex(index)) {
                        list.setSelectedIndex(index);
                    }
                    JPopupMenu menu = list == originalList ? buildOriginalRowPopup() : buildEditPopup();
                    menu.show(list, e.getX(), e.getY());
                }
            }
        };
        originalList.addMouseListener(openMenu);
        namesList.addMouseListener(openMenu);

        // double-click: left shows the row popup, right edits the name
        originalList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Pair pair = selected(originalList);
                    if (pair != null) {
                        buildOriginalRowPopup().show(originalList, e.getX(), e.getY());
                    }
                }
            }
        });
        namesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editName();
                }
            }
        });

        installKeys(originalList, true);
        installKeys(namesList, false);
    }

    private void installKeys(JList<Pair> list, boolean isOriginal) {
        InputMap keys = list.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = list.getActionMap();
        keys.put(KeyStroke.getKeyStroke("DELETE"), "exclude");
        actions.put("exclude", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                removeSelected();
            }
        });
        keys.put(KeyStroke.getKeyStroke("control O"), "open");
        actions.put("open", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                Pair pair = selected(isOriginal ? originalList : namesList);
                if (pair != null) {
                    openFile(pair.source());
                }
            }
        });
        // Keyboard shortcuts: Ctrl+L reveal, Ctrl+N edit name, Ctrl+M edit match
        keys.put(KeyStroke.getKeyStroke("control L"), "reveal");
        actions.put("reveal", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                Pair pair = selected(isOriginal ? originalList : namesList);
                if (pair != null) {
                    revealFile(pair.source());
                }
            }
        });
        keys.put(KeyStroke.getKeyStroke("control N"), "editNameKey");
        actions.put("editNameKey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                editName();
            }
        });
        keys.put(KeyStroke.getKeyStroke("control M"), "editMatch");
        actions.put("editMatch", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                editMatch();
            }
        });
        keys.put(KeyStroke.getKeyStroke("shift DELETE"), "excludeShift");
        actions.put("excludeShift", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                removeSelected();
            }
        });
        keys.put(KeyStroke.getKeyStroke("F2"), "editName");
        actions.put("editName", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                editName();
            }
        });
    }

    private void syncSelection(JList<Pair> from, JList<Pair> to) {
        if (!from.getValueIsAdjusting() && !to.getSelectedIndices().equals(from.getSelectedIndices())) {
            to.setSelectionInterval(from.getMinSelectionIndex(), from.getMaxSelectionIndex());
        }
    }

    /** Two-line cell: name + extension badge on top, the pairing partner underneath. */
    private static final class PairRenderer extends Box implements ListCellRenderer<Pair> {
        private final JLabel name = new JLabel();
        private final JLabel badge = new Badge();
        private final JLabel partner = new JLabel();
        private final boolean showSource;

        PairRenderer(boolean showSource) {
            super(BoxLayout.Y_AXIS);
            this.showSource = showSource;
            setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            setOpaque(true);
            name.setFont(Ui.title(13f));
            partner.setFont(Ui.body().deriveFont(11f));
            badge.setFont(Ui.body().deriveFont(10f));
            badge.setBorder(BorderFactory.createEmptyBorder(1, 7, 2, 7));
            JPanel top = new JPanel();
            top.setOpaque(false);
            top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
            top.add(name);
            top.add(Box.createHorizontalStrut(6));
            top.add(badge);
            top.add(Box.createHorizontalGlue());
            add(top);
            add(Box.createVerticalStrut(2));
            add(partner);
        }

        /** Unmatched rows are hidden from New Names: a zero-height cell keeps the shared model aligned. */
        private static final JComponent HIDDEN_CELL = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(0, 0);
            }
        };

        /** Row label: the file name without its extension — the badge already carries it. */
        private static String baseName(Path file) {
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Pair> list, Pair pair, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            // New Names stays empty until a file is matched
            if (!showSource && pair.status().equals(STATUS_NO_MATCH)) {
                return HIDDEN_CELL;
            }
            Path shown = showSource ? pair.source() : pair.target();
            Path other = showSource ? pair.target() : pair.source();
            // The New Names list shows the matched display name, not the file name
            name.setText(!showSource && pair.display() != null
                    ? pair.display() : baseName(shown));
            String partnerText = showSource && pair.display() != null
                    ? pair.display() : baseName(other);
            partner.setText(partnerText.equals(name.getText()) ? "" : partnerText);
            String ext = MediaScanner.extension(shown);
            badge.setText(ext.isEmpty() ? "" : ext.toLowerCase(java.util.Locale.ROOT));

            Color fg = Ui.text();
            Color muted = Ui.secondary();
            if (!isSelected) {
                name.setForeground(switch (pair.status()) {
                    case STATUS_RENAME -> new Color(0x30, 0xB0, 0x60);
                    case STATUS_UNCHANGED -> muted;
                    // Unmatched rows stay plain — red is reserved for conflicts
                    default -> fg;
                });
                partner.setForeground(muted);
                badge.setForeground(muted);
                setBackground(new Color(0, 0, 0, 0));
            } else {
                name.setForeground(fg);
                partner.setForeground(Ui.fade(fg, 45));
                badge.setForeground(fg);
                setBackground(Ui.accent());
            }
            return this;
        }

        /** Extension pill: a small rounded badge with the file type. */
        private static final class Badge extends JLabel {
            @Override
            protected void paintComponent(Graphics g) {
                if (!getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Ui.dark() ? new Color(255, 255, 255, 30) : new Color(0, 0, 0, 28));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        }
    }

    // ============================================================
    // loading files
    // ============================================================

    private void addFiles(List<Path> dropped) {
        List<Path> files = new java.util.ArrayList<>();
        for (Path path : dropped) {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    // A folder drop lists every entry, media files first
                    List<Path> entries = stream.filter(Files::isRegularFile).sorted().toList();
                    entries.stream().filter(MediaScanner::isMedia).forEach(files::add);
                    entries.stream().filter(f -> !MediaScanner.isMedia(f)).forEach(files::add);
                } catch (Exception e) {
                    statusLabel.setText("could not read " + path.getFileName());
                }
            } else {
                files.add(path);
            }
        }
        if (files.isEmpty()) {
            statusLabel.setText("no files in that drop");
            return;
        }
        for (Path file : files) {
            rows.addElement(new Pair(file, file, STATUS_NO_MATCH));
        }
        updateEmptyState();
        statusLabel.setText(rows.size() + " files — press Match to look them up");
    }

    /** Load: the OS file manager (Finder on macOS, Explorer on Windows). */
    private void loadFiles() {
        Frame owner = SwingUtilities.getWindowAncestor(this) instanceof Frame f ? f : null;
        FileDialog chooser = new FileDialog(owner, "Load files", FileDialog.LOAD);
        chooser.setMultipleMode(true);
        chooser.setVisible(true);
        File[] selected = chooser.getFiles();
        if (selected != null && selected.length > 0) {
            addFiles(java.util.Arrays.stream(selected).map(File::toPath).toList());
        }
    }

    private void updateEmptyState() {
        if (rows.isEmpty()) {
            ((CardLayout) originalCard.getLayout()).show(originalCard, "empty");
        } else {
            ((CardLayout) originalCard.getLayout()).show(originalCard, "list");
        }
        // New Names stays empty until Match produces a pairing
        ((CardLayout) namesCard.getLayout()).show(namesCard, hasMatched() ? "list" : "empty");
        // Rename is always live once files are loaded; the no-op guard reports instead
        renameButton.setEnabled(!rows.isEmpty());
    }

    private boolean hasMatched() {
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).status().equals(STATUS_NO_MATCH)) {
                return true;
            }
        }
        return false;
    }

    private JComponent buildNamesPlaceholder() {
        JPanel zone = new JPanel();
        zone.setOpaque(false);
        JLabel text = new JLabel("new names appear here after Match");
        text.setForeground(Ui.secondary());
        text.setFont(Ui.body().deriveFont(13f));
        zone.add(text);
        return zone;
    }

    private JComponent buildDropZone() {
        JPanel zone = new JPanel();
        zone.setOpaque(false);
        zone.setLayout(new BoxLayout(zone, BoxLayout.Y_AXIS));
        JLabel icon = new JLabel(Ui.icon("dropzone", 40));
        icon.setAlignmentX(CENTER_ALIGNMENT);
        JLabel text = new JLabel("Drop files or a folder here");
        text.setForeground(Ui.secondary());
        text.setFont(Ui.body().deriveFont(13f));
        text.setAlignmentX(CENTER_ALIGNMENT);
        JButton choose = new JButton("Choose…");
        choose.setAlignmentX(CENTER_ALIGNMENT);
        choose.addActionListener(e -> loadFiles());
        zone.add(Box.createVerticalGlue());
        zone.add(icon);
        zone.add(Box.createVerticalStrut(10));
        zone.add(text);
        zone.add(Box.createVerticalStrut(12));
        zone.add(choose);
        zone.add(Box.createVerticalGlue());
        return zone;
    }

    // ============================================================
    // Match → preview rows; Rename → apply
    // ============================================================

    private RenameService.Options options() {
        return new RenameService.Options(modeControl.getSelected(), format,
                (Double) minScoreSpinner.getValue());
    }

    private void match() {
        if (rows.isEmpty()) {
            statusLabel.setText("load some files first");
            return;
        }
        List<Path> files = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            files.add(rows.get(i).source());
        }
        matchButton.setEnabled(false);
        renameButton.setEnabled(false);
        statusLabel.setText("matching…");
        new SwingWorker<List<RenameController.Row>, Void>() {
            @Override
            protected List<RenameController.Row> doInBackground() throws Exception {
                Datasource datasource = Datasources.create(Config.load(), (String) dbBox.getSelectedItem(), false);
                return new RenameController(datasource)
                        .preview(files, options(), companionCheck.isSelected());
            }

            @Override
            protected void done() {
                try {
                    List<RenameController.Row> preview = get();
                    applyPreview(preview);
                    long planned = preview.stream().filter(r -> r.status().equals(STATUS_RENAME)).count();
                    statusLabel.setText(planned + " of " + preview.size() + " names planned");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("match failed");
                    JOptionPane.showMessageDialog(RenamePanel.this, cause.getMessage(),
                            "Match failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    matchButton.setEnabled(true);
                    renameButton.setEnabled(!rows.isEmpty());
                }
            }
        }.execute();
    }

    /** Replace the row model with the preview, matched rows first. */
    private void applyPreview(List<RenameController.Row> preview) {
        rows.clear();
        List<RenameController.Row> ordered = new java.util.ArrayList<>(preview);
        ordered.sort(java.util.Comparator.comparingInt(r -> r.status().equals(STATUS_NO_MATCH) ? 1 : 0));
        for (RenameController.Row row : ordered) {
            rows.addElement(new Pair(row.source(), row.target(), row.status(), displayFor(row)));
        }
        lastVars = varsFor(preview);
        updateEmptyState();
    }

    /** New Names shows the matched display name ("Up (2009)"), not the file name. */
    private static String displayFor(RenameController.Row row) {
        if (row.status().equals(STATUS_NO_MATCH)) {
            return null;
        }
        String name = row.target().getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Extension Preserve/Override: with Override a rendered name that
     * carries its own media extension wins; otherwise the source extension is kept.
     */
    private Path targetFor(String renderedName, Path source) {
        String name = renderedName;
        int dot = name.lastIndexOf('.');
        if (extensionControl.getSelected().equals("override") && dot > 0
                && MediaScanner.isMedia(java.nio.file.Paths.get(name))) {
            return source.resolveSibling(name);
        }
        return source.resolveSibling(name + "." + MediaScanner.extension(source));
    }

    /** Re-run the current format over the variables from the last Match. */
    private void applyFormat() {
        if (lastVars.isEmpty()) {
            match();
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Pair pair = rows.get(i);
            Map<String, Object> vars = lastVars.get(pair.source());
            if (vars == null) {
                continue;
            }
            String name = RenameService.sanitize(FormatEngine.format(format, vars));
            Path target = targetFor(name, pair.source());
            rows.set(i, new Pair(pair.source(), target,
                    target.equals(pair.source()) ? STATUS_UNCHANGED : STATUS_RENAME, name));
        }
        renameButton.setEnabled(!rows.isEmpty());
        statusLabel.setText("format applied to " + lastVars.size() + " matches");
    }

    /**
     * Best-effort recovery of format variables from planned targets: the target
     * name is the rendered format, so Edit Format can re-render against samples.
     */
    private Map<Path, Map<String, Object>> varsFor(List<RenameController.Row> preview) {
        Map<Path, Map<String, Object>> vars = new java.util.LinkedHashMap<>();
        for (RenameController.Row row : preview) {
            if (!row.status().equals(STATUS_RENAME)) {
                continue;
            }
            String targetName = row.target().getFileName().toString();
            int dot = targetName.lastIndexOf('.');
            String base = dot > 0 ? targetName.substring(0, dot) : targetName;
            vars.put(row.source(), Map.<String, Object>of("custom", base, "n", base));
        }
        return vars;
    }

    private void rename() {
        List<RenameController.Row> planned = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Pair pair = rows.get(i);
            if (pair.status().equals(STATUS_RENAME)) {
                planned.add(new RenameController.Row(pair.source(), pair.target(), pair.status()));
            }
        }
        if (planned.isEmpty()) {
            // No-op guard: Rename always runs and reports when nothing would change
            statusLabel.setText("Files and Names are already optimally aligned.");
            return;
        }
        // Rename executes immediately: no confirm dialog
        renameButton.setEnabled(false);
        statusLabel.setText("running…");
        TransferAction action = TransferAction.valueOf(actionControl.getSelected().toUpperCase(java.util.Locale.ROOT));
        ConflictPolicy conflict = ConflictPolicy.valueOf(conflictControl.getSelected().toUpperCase(java.util.Locale.ROOT));
        new SwingWorker<List<RenameController.Row>, Void>() {
            @Override
            protected List<RenameController.Row> doInBackground() throws Exception {
                Datasource datasource = Datasources.create(Config.load(), (String) dbBox.getSelectedItem(), false);
                List<RenameController.Row> results =
                        new RenameController(datasource).apply(planned, conflict, action);
                history.record(results.stream()
                        .filter(RenameController.Row::applied)
                        .map(r -> new app.renami.rename.RenameOperation(r.source(), r.target()))
                        .toList(), action);
                return results;
            }

            @Override
            protected void done() {
                try {
                    List<RenameController.Row> results = get();
                    long applied = results.stream().filter(RenameController.Row::applied).count();
                    // drop applied pairings, keep the rest with their result status
                    java.util.Set<Path> done = new java.util.HashSet<>();
                    Map<Path, String> failed = new java.util.HashMap<>();
                    for (RenameController.Row row : results) {
                        if (row.status().equals(STATUS_RENAME)) {
                            continue;
                        }
                        if (row.applied()) {
                            done.add(row.source());
                        } else {
                            failed.put(row.source(), row.status());
                        }
                    }
                    for (int i = rows.size() - 1; i >= 0; i--) {
                        Pair pair = rows.get(i);
                        if (done.contains(pair.source())) {
                            rows.remove(i);
                        } else if (failed.containsKey(pair.source())) {
                            rows.set(i, new Pair(pair.source(), pair.target(), failed.get(pair.source())));
                        }
                    }
                    statusLabel.setText(applied + " files renamed. Use `renami undo` to revert.");
                    // "Post Process" option: hand the freshly applied files to the Post-Process view
                    if (postProcessCheck.isSelected() && postProcessHandler != null) {
                        List<Path> moved = results.stream()
                                .filter(RenameController.Row::applied)
                                .map(RenameController.Row::target)
                                .toList();
                        if (!moved.isEmpty()) {
                            postProcessHandler.accept(moved);
                        }
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("rename failed");
                    JOptionPane.showMessageDialog(RenamePanel.this, cause.getMessage(),
                            "Rename failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    updateEmptyState();
                }
            }
        }.execute();
    }

    // ============================================================
    // row operations
    // ============================================================

    private Pair selected(JList<Pair> list) {
        return list.getSelectedValue();
    }

    private void removeSelected() {
        removeIndices(originalList.getSelectedIndices());
    }

    private void removeIndices(int[] indices) {
        for (int i = indices.length - 1; i >= 0; i--) {
            if (indices[i] < rows.size()) {
                rows.remove(indices[i]);
            }
        }
        updateEmptyState();
    }

    /** Reorder the selection within the shared model — Align Up/Down. */
    private void moveSelected(int delta) {
        int[] indices = originalList.getSelectedIndices();
        if (indices.length == 0) {
            indices = namesList.getSelectedIndices();
        }
        if (indices.length == 0) {
            return;
        }
        boolean forward = delta > 0;
        for (int i = forward ? indices.length - 1 : 0; forward ? i >= 0 : i < indices.length;
             i += forward ? -1 : 1) {
            int from = indices[i];
            int to = from + delta;
            if (to < 0 || to >= rows.size() || originalList.isSelectedIndex(to)) {
                continue;
            }
            rows.add(to, rows.remove(from));
            indices[i] = to;
        }
        originalList.setSelectedIndices(indices);
        namesList.setSelectedIndices(indices);
    }

    private void openFile(Path file) {
        try {
            java.awt.Desktop.getDesktop().open(file.toFile());
        } catch (Exception e) {
            statusLabel.setText("could not open " + file.getFileName());
        }
    }

    private void revealFile(Path file) {
        try {
            java.awt.Desktop.getDesktop().open(file.getParent().toFile());
        } catch (Exception e) {
            statusLabel.setText("could not reveal " + file.getFileName());
        }
    }

    // ============================================================
    // popups (per-list tool strips)
    // ============================================================

    private JPopupMenu buildClearPopup() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Original Files", () -> {
            rows.clear();
            lastVars = Map.of();
            updateEmptyState();
            statusLabel.setText("cleared");
        }));
        menu.add(item("New Names", () -> {
            for (int i = 0; i < rows.size(); i++) {
                Pair pair = rows.get(i);
                rows.set(i, new Pair(pair.source(), pair.source(), STATUS_NO_MATCH));
            }
            renameButton.setEnabled(false);
            statusLabel.setText("new names reset");
        }));
        menu.addSeparator();
        menu.add(item("Clear All", () -> {
            rows.clear();
            lastVars = Map.of();
            updateEmptyState();
            statusLabel.setText("cleared");
        }));
        return menu;
    }

    private JPopupMenu buildLoadNamesPopup() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Load from Directory Index…", this::loadNamesFromDirectoryIndex));
        menu.add(item("Load from Text File…", this::loadNamesFromFile));
        menu.add(item("Paste from Clipboard", this::pasteNames));
        return menu;
    }

    private void pasteNames() {
        try {
            String text = java.awt.Toolkit.getDefaultToolkit()
                    .getSystemClipboard().getData(DataFlavor.stringFlavor).toString();
            setNamesFromText(text);
        } catch (Exception e) {
            statusLabel.setText("clipboard is empty");
        }
    }

    private void loadNamesFromFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                setNamesFromText(Files.readString(chooser.getSelectedFile().toPath()));
            } catch (Exception e) {
                statusLabel.setText("could not read that file");
            }
        }
    }

    /** Entry point for sibling views (Episodes, Filter) to feed target names in. */
    void loadNamesFromText(String text) {
        setNamesFromText(text);
    }

    /** Map text lines onto selected (or all) rows, in order — Load Names. */
    private void setNamesFromText(String text) {
        List<String> lines = new java.util.ArrayList<>();
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        int[] indices = namesList.getSelectedIndices();
        if (indices.length == 0) {
            indices = new int[rows.size()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = i;
            }
        }
        int applied = 0;
        for (int i = 0; i < Math.min(lines.size(), indices.length); i++) {
            Pair pair = rows.get(indices[i]);
            String name = RenameService.sanitize(lines.get(i));
            Path target = targetFor(name, pair.source());
            rows.set(indices[i], new Pair(pair.source(), target,
                    target.equals(pair.source()) ? STATUS_UNCHANGED : STATUS_RENAME, name));
            applied++;
        }
        renameButton.setEnabled(!rows.isEmpty());
        statusLabel.setText(applied + " names loaded");
    }

    private JPopupMenu buildSettingsPopup() {
        JPopupMenu menu = new JPopupMenu();
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 10);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0;
        panel.add(Ui.caption("Mode"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(modeControl, c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(Ui.caption("Database"), c);
        c.gridx = 1; c.gridy = 1;
        panel.add(dbBox, c);
        c.gridx = 0; c.gridy = 2;
        panel.add(Ui.caption("Min. score"), c);
        c.gridx = 1; c.gridy = 2;
        panel.add(minScoreSpinner, c);
        c.gridx = 0; c.gridy = 3;
        panel.add(Ui.caption("Action"), c);
        c.gridx = 1; c.gridy = 3;
        panel.add(actionControl, c);
        c.gridx = 0; c.gridy = 4;
        panel.add(Ui.caption("Conflict"), c);
        c.gridx = 1; c.gridy = 4;
        panel.add(conflictControl, c);
        c.gridx = 0; c.gridy = 5;
        panel.add(Ui.caption("Extension"), c);
        c.gridx = 1; c.gridy = 5;
        panel.add(extensionControl, c);
        c.gridx = 0; c.gridy = 6; c.gridwidth = 2;
        panel.add(companionCheck, c);
        c.gridy = 7;
        panel.add(postProcessCheck, c);
        c.gridy = 8; c.fill = GridBagConstraints.HORIZONTAL;
        JButton editFormat = new JButton("Edit Format…  " + format);
        editFormat.addActionListener(e -> editFormat());
        panel.add(editFormat, c);
        menu.add(panel);
        return menu;
    }

    /** Fetch & Match Data popup: branded datasources per mode + options. */
    private JPopupMenu buildFetchPopup() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(groupLabel("Episode Mode"));
        menu.add(brandedItem("TheMovieDB::TV", "01B4E5", () -> fetchMatch("tmdb", "episode")));
        menu.add(brandedItem("TheTVDB", "00956A", () -> fetchMatch("tvdb", "episode")));
        menu.add(brandedItem("TVmaze", "2E7DD1", () -> fetchMatch("tvmaze", "episode")));
        menu.add(brandedItem("Kitsu (Anime)", "7E57C2", () -> fetchMatch("kitsu", "episode")));
        menu.add(groupLabel("Movie Mode"));
        menu.add(brandedItem("TheMovieDB", "01B4E5", () -> fetchMatch("tmdb", "movie")));
        menu.add(brandedItem("OMDb", "F5C518", () -> fetchMatch("omdb", "movie")));
        menu.add(groupLabel("Music Mode"));
        menu.add(disabledItem("AcoustID", "4CAF50", "not yet supported"));
        menu.add(disabledItem("ID3 Tags", "9E9E9E", "not yet supported"));
        menu.add(groupLabel("Smart Mode"));
        menu.add(item("Automatic", () -> {
            modeControl.setSelected("auto");
            dbBox.setSelectedItem("auto");
            match();
        }));
        menu.add(item("Attributes (offline)", () -> {
            modeControl.setSelected("local");
            dbBox.setSelectedItem("local");
            match();
        }));
        menu.addSeparator();
        menu.add(item("Edit Format…", this::editFormat));
        menu.add(item("Preferences…", () -> buildSettingsPopup().show(namesList, 40, 40)));
        return menu;
    }

    private void fetchMatch(String db, String mode) {
        dbBox.setSelectedItem(db);
        modeControl.setSelected(mode);
        match();
    }

    /** Menu row with the provider's brand dot. */
    private static JMenuItem brandedItem(String label, String hex, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.setIcon(dotIcon(hex));
        item.addActionListener(e -> action.run());
        return item;
    }

    private static JMenuItem disabledItem(String label, String hex, String why) {
        JMenuItem item = new JMenuItem(label);
        item.setIcon(dotIcon(hex));
        item.setEnabled(false);
        item.setToolTipText(why);
        return item;
    }

    /** Small filled circle in the provider's brand color. */
    private static Icon dotIcon(String hex) {
        Color color = Ui.hex(hex);
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x, y, getIconWidth() - 1, getIconHeight() - 1);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 10;
            }

            @Override
            public int getIconHeight() {
                return 10;
            }
        };
    }

    /** History dialog: the journal browser lives in a toolbar dialog, not the rail. */
    private void showHistoryDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner instanceof Frame f ? f : null, "History", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(new HistoryPanel());
        dialog.pack();
        if (dialog.getWidth() < 780 || dialog.getHeight() < 560) {
            dialog.setSize(780, 560);
        }
        dialog.setLocationRelativeTo(owner);
        // macOS sometimes hands back a collapsed peer: re-assert the size once shown
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                dialog.setSize(780, 560);
                dialog.setLocationRelativeTo(owner);
            }
        });
        dialog.setVisible(true);
        // the journal may have changed while the dialog was open
        updateEmptyState();
    }

    private static JMenuItem groupLabel(String text) {
        JMenuItem label = new JMenuItem(text);
        label.setEnabled(false);
        return label;
    }

    private JPopupMenu buildEditPopup() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Edit Name…  (F2)", this::editName));
        menu.add(item("Edit Match…", this::editMatch));
        menu.add(item("Edit Format…", this::editFormat));
        menu.addSeparator();
        Pair pair = selected(namesList);
        if (pair != null) {
            menu.add(item("Open", () -> openFile(pair.source())));
            menu.add(item("Reveal in Finder", () -> revealFile(pair.source())));
        }
        menu.add(item("Inspect", this::inspectMatch));
        return menu;
    }

    private JPopupMenu buildOriginalRowPopup() {
        JPopupMenu menu = new JPopupMenu();
        Pair pair = selected(originalList);
        if (pair != null) {
            menu.add(item("Open", () -> openFile(pair.source())));
            menu.add(item("Reveal in Finder", () -> revealFile(pair.source())));
            menu.add(item("Rename…", () -> renameFileDirect(pair.source())));
            menu.add(item("Move to Trash", () -> moveToTrash(pair.source())));
            menu.addSeparator();
        }
        menu.add(item("Exclude Selected Items", this::removeSelected));
        return menu;
    }

    private static JMenuItem item(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> action.run());
        return item;
    }

    private void editName() {
        Pair pair = selected(namesList);
        if (pair == null) {
            pair = selected(originalList);
        }
        if (pair == null) {
            return;
        }
        String current = pair.target().getFileName().toString();
        String input = JOptionPane.showInputDialog(this, "New file name:", current);
        if (input == null || input.isBlank()) {
            return;
        }
        String name = RenameService.sanitize(input);
        if (!name.contains(".")) {
            name = name + "." + MediaScanner.extension(pair.source());
        }
        Path target = pair.source().resolveSibling(name);
        int index = indexOfSource(pair.source());
        if (index >= 0) {
            String shown = target.getFileName().toString();
            int dot = shown.lastIndexOf('.');
            rows.set(index, new Pair(pair.source(), target,
                    target.equals(pair.source()) ? STATUS_UNCHANGED : STATUS_RENAME,
                    dot > 0 ? shown.substring(0, dot) : shown));
            renameButton.setEnabled(!rows.isEmpty());
        }
    }

    /** "Edit Match": pick a different movie/show from the datasource. */
    private void editMatch() {
        Pair pair = selected(namesList);
        if (pair == null) {
            pair = selected(originalList);
        }
        if (pair == null) {
            return;
        }
        final Path source = pair.source();
        statusLabel.setText("fetching candidates…");
        new SwingWorker<List<String[]>, Void>() {
            @Override
            protected List<String[]> doInBackground() throws Exception {
                Datasource db = Datasources.create(Config.load(), (String) dbBox.getSelectedItem(), false);
                RenameService svc = new RenameService(db,
                        new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
                boolean episode = svc.episodeOf(source) != null;
                List<String[]> rows = new java.util.ArrayList<>();
                if (episode) {
                    app.renami.parse.EpisodeNumber ep = svc.episodeOf(source);
                    for (RenameService.ShowCandidate c : svc.showCandidates(source)) {
                        rows.add(new String[]{"show", c.show().name(),
                                c.show().year() > 0 ? String.valueOf(c.show().year()) : "",
                                String.format("%.0f%%", c.score() * 100),
                                String.valueOf(ep.season()), String.valueOf(ep.episode())});
                    }
                } else {
                    for (RenameService.Candidate c : svc.movieCandidates(source)) {
                        rows.add(new String[]{"movie", c.movie().title(),
                                c.movie().year() > 0 ? String.valueOf(c.movie().year()) : "",
                                String.format("%.0f%%", c.score() * 100), "", ""});
                    }
                }
                return rows;
            }

            @Override
            protected void done() {
                try {
                    List<String[]> found = get();
                    if (found.isEmpty()) {
                        statusLabel.setText("no candidates found");
                        return;
                    }
                    chooseCandidate(source, found);
                } catch (Exception ex) {
                    statusLabel.setText("candidate lookup failed");
                }
            }
        }.execute();
    }

    /** Table dialog over the fetched candidates; applies the chosen match to the row. */
    private void chooseCandidate(Path source, List<String[]> found) {
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"Title", "Year", "Score"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (String[] c : found) {
            model.addRow(new Object[]{c[1], c[2], c[3]});
        }
        JTable table = new JTable(model);
        Ui.styleTable(table);
        table.setRowSelectionInterval(0, 0);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(460, 260));
        if (JOptionPane.showConfirmDialog(this, scroll, "Edit Match — pick the correct title",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        String[] chosen = found.get(table.getSelectedRow());
        Integer year = chosen[2].isEmpty() ? null : Integer.parseInt(chosen[2]);
        Map<String, Object> vars;
        if ("show".equals(chosen[0])) {
            app.renami.parse.EpisodeNumber ep =
                    new app.renami.parse.EpisodeNumber(Integer.parseInt(chosen[4]), Integer.parseInt(chosen[5]));
            vars = FormatEngine.standardVars(chosen[1], year, ep.format(), "", ep.season(), ep.episode());
        } else {
            vars = FormatEngine.standardVars(chosen[1], year, null, null, null, null);
        }
        String name = RenameService.sanitize(FormatEngine.format(format, vars));
        Path target = targetFor(name, source);
        int index = indexOfSource(source);
        if (index >= 0) {
            rows.set(index, new Pair(source, target,
                    target.equals(source) ? STATUS_UNCHANGED : STATUS_RENAME, name));
            Map<Path, Map<String, Object>> updated = new java.util.LinkedHashMap<>(lastVars);
            updated.put(source, vars);
            lastVars = updated;
            renameButton.setEnabled(!rows.isEmpty());
        }
        statusLabel.setText("match changed to " + chosen[1]);
    }

    /** "Rename…" on the Original Files list: rename the file on disk now. */
    private void renameFileDirect(Path file) {
        String current = file.getFileName().toString();
        String input = JOptionPane.showInputDialog(this, "Rename file on disk:", current);
        if (input == null || input.isBlank()) {
            return;
        }
        try {
            Path target = file.resolveSibling(RenameService.sanitize(input));
            if (target.equals(file)) {
                return;
            }
            Files.move(file, target);
            history.record(List.of(new app.renami.rename.RenameOperation(file, target)));
            int index = indexOfSource(file);
            if (index >= 0) {
                Pair pair = rows.get(index);
                Path newTarget = pair.target().equals(file) ? target : pair.target();
                rows.set(index, new Pair(target, newTarget,
                        newTarget.equals(target) ? STATUS_UNCHANGED : STATUS_RENAME));
                renameButton.setEnabled(!rows.isEmpty());
            }
            statusLabel.setText("renamed " + current);
        } catch (Exception e) {
            statusLabel.setText("rename failed: " + e.getMessage());
        }
    }

    /** "Move to Trash" on the Original Files list. */
    private void moveToTrash(Path file) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Move " + file.getFileName() + " to the Trash?", "Move to Trash",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            boolean trashed = desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)
                    && desktop.moveToTrash(file.toFile());
            if (!trashed) {
                statusLabel.setText("could not move to Trash");
                return;
            }
            int index = indexOfSource(file);
            if (index >= 0) {
                rows.remove(index);
                renameButton.setEnabled(!rows.isEmpty());
            }
            statusLabel.setText("moved to Trash: " + file.getFileName());
        } catch (Exception e) {
            statusLabel.setText("could not move to Trash: " + e.getMessage());
        }
    }

    /** "Load from Directory Index": names come from the files in a folder. */
    private void loadNamesFromDirectoryIndex() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Load names from the files in this folder");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try (var stream = Files.list(chooser.getSelectedFile().toPath())) {
            List<String> names = stream.filter(Files::isRegularFile)
                    .map(p -> {
                        String n = p.getFileName().toString();
                        int dot = n.lastIndexOf('.');
                        return dot > 0 ? n.substring(0, dot) : n;
                    })
                    .sorted()
                    .toList();
            setNamesFromText(String.join("\n", names));
        } catch (Exception e) {
            statusLabel.setText("could not read that folder");
        }
    }

    private int indexOfSource(Path source) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).source().equals(source)) {
                return i;
            }
        }
        return -1;
    }

    private void inspectMatch() {
        Pair pair = selected(namesList);
        if (pair == null) {
            return;
        }
        JOptionPane.showMessageDialog(this,
                "Original: " + pair.source() + "\nNew name: " + pair.target() + "\nStatus: " + pair.status(),
                "Match details", JOptionPane.INFORMATION_MESSAGE);
    }

    // ============================================================
    // Edit Format dialog
    // ============================================================

    private void editFormat() {
        JTextField field = new JTextField(format, 28);
        field.setFont(Ui.mono(13f));
        DefaultTableModel previewModel =
                new DefaultTableModel(new Object[]{"File", "Preview"}, 0) {
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return false;
                    }
                };
        JTable previewTable = new JTable(previewModel);
        Ui.styleTable(previewTable);
        JScrollPane scroll = new JScrollPane(previewTable);
        scroll.setPreferredSize(new Dimension(460, 180));
        scroll.setBorder(BorderFactory.createEmptyBorder());

        Runnable refresh = () -> {
            previewModel.setRowCount(0);
            int shown = 0;
            for (int i = 0; i < rows.size() && shown < 8; i++) {
                Pair pair = rows.get(i);
                Map<String, Object> vars = lastVars.get(pair.source());
                String preview = vars != null
                        ? RenameService.sanitize(FormatEngine.format(field.getText(), vars))
                        : "—";
                previewModel.addRow(new Object[]{pair.source().getFileName(), preview});
                shown++;
            }
        };
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                refresh.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                refresh.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                refresh.run();
            }
        });
        refresh.run();

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.add(Ui.caption("Format expression — Groovy inside { }"), BorderLayout.NORTH);
        JPanel fieldRow = new JPanel(new BorderLayout(8, 0));
        fieldRow.add(field, BorderLayout.CENTER);
        JButton help = new JButton("Variables");
        help.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "{n} name · {y} year · {t} episode title · {s00e00} S01E02 · {s}/{e} numbers · {fn} filename guess",
                "Format variables", JOptionPane.INFORMATION_MESSAGE));
        fieldRow.add(help, BorderLayout.EAST);
        body.add(fieldRow, BorderLayout.CENTER);
        body.add(scroll, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(this, body, "Edit Format",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION && !field.getText().isBlank()) {
            format = field.getText().trim();
            applyFormat();
        }
    }

    // ============================================================
    // Presets (F1–F12 style quick formats, persisted in ~/.renami/presets.json)
    // ============================================================

    private JPopupMenu buildPresetsPopup() {
        JPopupMenu menu = new JPopupMenu();
        List<Map<String, String>> presets = loadPresets();
        for (int i = 0; i < presets.size(); i++) {
            Map<String, String> preset = presets.get(i);
            String label = (i < 12 ? "F" + (i + 1) + "  " : "") + preset.getOrDefault("name", preset.get("format"));
            menu.add(item(label, () -> {
                format = preset.get("format");
                applyFormat();
            }));
        }
        menu.addSeparator();
        menu.add(item("Edit Presets…", this::editPresets));
        return menu;
    }

    private List<Map<String, String>> loadPresets() {
        try {
            Path file = Config.dataDir().resolve("presets.json");
            if (Files.isRegularFile(file)) {
                return new Gson().fromJson(Files.readString(file),
                        new TypeToken<List<Map<String, String>>>() {
                        }.getType());
            }
        } catch (Exception ignored) {
            // fall through to defaults
        }
        // The 14 example presets + a Jellyfin favourite, adapted to Renami formats
        return List.of(
                Map.of("name", "Organize Movies for Jellyfin", "format", "{n} ({y})/{n} ({y})"),
                Map.of("name", "Organize Episodes for Jellyfin", "format", "{n}/Season {s}/{n} - {s00e00} - {t}"),
                Map.of("name", "Match by Episode Title", "format", "{t}"),
                Map.of("name", "Episode Number Only", "format", "{s00e00}"),
                Map.of("name", "Season and Episode Numbers", "format", "{sn} {en}"),
                Map.of("name", "Name and Episode Number", "format", "{n} - {s00e00}"),
                Map.of("name", "Name, Number and Title", "format", "{n} - {s00e00} - {t}"),
                Map.of("name", "Name with Year", "format", "{n} ({y})"),
                Map.of("name", "Original Media Title", "format", "{fn}"),
                Map.of("name", "File to Folder Name", "format", "{fn}/{fn}"),
                Map.of("name", "Clean Name", "format", "{fn.replaceAll('[._]', ' ')}"),
                Map.of("name", "Transliterate Non-ASCII", "format", "{fn.replaceAll('[^\\\\x20-\\\\x7E]', '_')}"),
                Map.of("name", "Year First", "format", "({y}) {n}"),
                Map.of("name", "Title Case Episodes", "format", "{n} - {s00e00}"),
                Map.of("name", "Jellyfin Movies", "format", "{n} ({y})/{n} ({y})"));
    }

    /** F1–F12 apply presets 1–12. */
    private void installPresetKeys() {
        InputMap keys = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actions = getActionMap();
        for (int i = 0; i < 12; i++) {
            final int index = i;
            KeyStroke key = KeyStroke.getKeyStroke("F" + (i + 1));
            keys.put(key, "preset" + index);
            actions.put("preset" + index, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    List<Map<String, String>> presets = loadPresets();
                    if (index < presets.size()) {
                        format = presets.get(index).get("format");
                        applyFormat();
                        statusLabel.setText("preset F" + (index + 1) + ": "
                                + presets.get(index).getOrDefault("name", format));
                    }
                }
            });
        }
    }

    private void editPresets() {
        List<Map<String, String>> presets = new java.util.ArrayList<>(loadPresets());
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Name", "Format"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
        for (Map<String, String> preset : presets) {
            model.addRow(new Object[]{preset.getOrDefault("name", ""), preset.getOrDefault("format", "")});
        }
        JTable table = new JTable(model);
        Ui.styleTable(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(460, 220));

        JPanel body = new JPanel(new BorderLayout(0, 8));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton add = new JButton("Add");
        add.addActionListener(e -> model.addRow(new Object[]{"New preset", "{n}"}));
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                model.removeRow(row);
            }
        });
        buttons.add(add);
        buttons.add(remove);
        body.add(buttons, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, body, "Edit Presets",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        List<Map<String, String>> saved = new java.util.ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            String name = String.valueOf(model.getValueAt(row, 0));
            String fmt = String.valueOf(model.getValueAt(row, 1));
            if (!fmt.isBlank()) {
                saved.add(Map.of("name", name.isBlank() ? fmt : name, "format", fmt));
            }
        }
        try {
            Path file = Config.dataDir().resolve("presets.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(saved));
            statusLabel.setText(saved.size() + " presets saved");
        } catch (Exception e) {
            statusLabel.setText("could not save presets");
        }
    }

    // ============================================================
    // small button factories
    // ============================================================

    private static JButton iconButton(String icon, String tooltip, java.awt.event.ActionListener action) {
        JButton button = new JButton(Ui.icon(icon, 18));
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(4, 4, 4, 4));
        button.addActionListener(action);
        return button;
    }

    private static JButton textButton(String text, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.addActionListener(action);
        return button;
    }

    private static JButton popupButton(String icon, String tooltip,
                                       java.util.function.Supplier<JPopupMenu> menu) {
        JButton button = new JButton(Ui.icon(icon, 18));
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(4, 4, 4, 4));
        button.addActionListener(e -> menu.get().show(button, 0, button.getHeight()));
        return button;
    }
}
