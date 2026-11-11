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
 * The rename view: FileBot's dual-panel layout — "Original Files" on the left,
 * "New Names" on the right, Match/Rename in the center column, each list with
 * its own tool strip — dressed in the 2026-macOS design language.
 *
 * One shared row model drives both lists, so the two panels can never drift
 * out of alignment (FileBot needs a whole "Match/align" algorithm for that).
 */
public final class RenamePanel extends JPanel {

    /** One file pairing: original path, planned target, and its state. */
    private record Pair(Path source, Path target, String status) {
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
    private final JComboBox<String> dbBox =
            new JComboBox<>(new String[]{"auto", "tmdb", "tvdb", "omdb", "kitsu", "local"});
    private final JSpinner minScoreSpinner =
            new JSpinner(new SpinnerNumberModel(0.60, 0.0, 1.0, 0.05));
    private final JCheckBox companionCheck = new JCheckBox("Rename companion files", true);
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

        matchButton.addActionListener(e -> match());
        renameButton.addActionListener(e -> rename());
        renameButton.setEnabled(false);
        // FileBot: right-click Match → Fetch popup, right-click Rename → Settings popup
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
        toolbar.add(iconButton("history", "Open History", e -> openHistory()));
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
        toolbar.add(textButton("Fetch Data", e -> match()));
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
        column.add(centerButton(matchButton, "match", 92));
        column.add(Box.createVerticalStrut(14));
        column.add(centerButton(renameButton, "rename", 92));
        column.add(Box.createVerticalGlue());
        return column;
    }

    /** Icon-over-text action button, FileBot's center column, our styling. */
    private static JComponent centerButton(JButton button, String icon, int size) {
        button.setIcon(Ui.icon(icon, size - 34));
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

        // double-click: left opens the file, right edits the name
        originalList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Pair pair = selected(originalList);
                    if (pair != null) {
                        openFile(pair.source());
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

    /** Two-line cell: file name on top, the pairing partner underneath. */
    private static final class PairRenderer extends Box implements ListCellRenderer<Pair> {
        private final JLabel name = new JLabel();
        private final JLabel partner = new JLabel();
        private final boolean showSource;

        PairRenderer(boolean showSource) {
            super(BoxLayout.Y_AXIS);
            this.showSource = showSource;
            setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            setOpaque(true);
            name.setFont(Ui.title(13f));
            partner.setFont(Ui.body().deriveFont(11f));
            add(name);
            add(Box.createVerticalStrut(2));
            add(partner);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Pair> list, Pair pair, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Path shown = showSource ? pair.source() : pair.target();
            Path other = showSource ? pair.target() : pair.source();
            name.setText(shown.getFileName().toString());
            partner.setText(other.getFileName().toString());

            Color fg = Ui.text();
            Color muted = Ui.secondary();
            if (!isSelected) {
                name.setForeground(switch (pair.status()) {
                    case STATUS_RENAME -> new Color(0x30, 0xB0, 0x60);
                    case STATUS_UNCHANGED -> muted;
                    case STATUS_NO_MATCH -> new Color(0xE0, 0x4A, 0x4A);
                    default -> fg;
                });
                partner.setForeground(muted);
                setBackground(new Color(0, 0, 0, 0));
            } else {
                name.setForeground(fg);
                partner.setForeground(Ui.fade(fg, 45));
                setBackground(Ui.accent());
            }
            return this;
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
                    stream.filter(Files::isRegularFile)
                            .filter(f -> MediaScanner.isMedia(f))
                            .sorted()
                            .forEach(files::add);
                } catch (Exception e) {
                    statusLabel.setText("could not read " + path.getFileName());
                }
            } else {
                files.add(path);
            }
        }
        if (files.isEmpty()) {
            statusLabel.setText("no media files in that drop");
            return;
        }
        for (Path file : files) {
            rows.addElement(new Pair(file, file, STATUS_NO_MATCH));
        }
        updateEmptyState();
        statusLabel.setText(rows.size() + " files — press Match to look them up");
    }

    private void loadFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            addFiles(java.util.Arrays.stream(chooser.getSelectedFiles()).map(File::toPath).toList());
        }
    }

    private void updateEmptyState() {
        if (rows.isEmpty()) {
            ((CardLayout) originalCard.getLayout()).show(originalCard, "empty");
            ((CardLayout) namesCard.getLayout()).show(namesCard, "empty");
        } else {
            ((CardLayout) originalCard.getLayout()).show(originalCard, "list");
            ((CardLayout) namesCard.getLayout()).show(namesCard, "list");
        }
        renameButton.setEnabled(!rows.isEmpty() && hasPlanned());
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

    private boolean hasPlanned() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).status().equals(STATUS_RENAME)) {
                return true;
            }
        }
        return false;
    }

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
                    renameButton.setEnabled(hasPlanned());
                }
            }
        }.execute();
    }

    /** Replace the row model with the preview, keeping the format variables around. */
    private void applyPreview(List<RenameController.Row> preview) {
        rows.clear();
        for (RenameController.Row row : preview) {
            rows.addElement(new Pair(row.source(), row.target(), row.status()));
        }
        lastVars = varsFor(preview);
        updateEmptyState();
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
            Path target = pair.source().resolveSibling(name + "." + MediaScanner.extension(pair.source()));
            rows.set(i, new Pair(pair.source(), target,
                    target.equals(pair.source()) ? STATUS_UNCHANGED : STATUS_RENAME));
        }
        renameButton.setEnabled(hasPlanned());
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
            return;
        }
        int confirmed = JOptionPane.showConfirmDialog(this,
                "Execute " + planned.size() + " renames?", "Confirm rename", JOptionPane.YES_NO_OPTION);
        if (confirmed != JOptionPane.YES_OPTION) {
            return;
        }
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

    /** Reorder the selection within the shared model — FileBot's Align Up/Down. */
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
    // popups (FileBot's per-list tool strips)
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
        menu.add(item("Paste from Clipboard", this::pasteNames));
        menu.add(item("Load from Text File…", this::loadNamesFromFile));
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

    /** Map text lines onto selected (or all) rows, in order — FileBot's Load Names. */
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
            Path target = pair.source().resolveSibling(name + "." + MediaScanner.extension(pair.source()));
            rows.set(indices[i], new Pair(pair.source(), target,
                    target.equals(pair.source()) ? STATUS_UNCHANGED : STATUS_RENAME));
            applied++;
        }
        renameButton.setEnabled(hasPlanned());
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
        c.gridx = 0; c.gridy = 5; c.gridwidth = 2;
        panel.add(companionCheck, c);
        c.gridy = 6; c.fill = GridBagConstraints.HORIZONTAL;
        JButton editFormat = new JButton("Edit Format…  " + format);
        editFormat.addActionListener(e -> editFormat());
        panel.add(editFormat, c);
        menu.add(panel);
        return menu;
    }

    /** FileBot's Fetch & Match Data popup: datasource groups + format + preferences. */
    private JPopupMenu buildFetchPopup() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(groupLabel("Episode Mode"));
        for (String db : List.of("tvdb", "tmdb")) {
            menu.add(item("  " + db, () -> {
                dbBox.setSelectedItem(db);
                modeControl.setSelected("episode");
                match();
            }));
        }
        menu.add(groupLabel("Movie Mode"));
        for (String db : List.of("tmdb", "omdb")) {
            menu.add(item("  " + db, () -> {
                dbBox.setSelectedItem(db);
                modeControl.setSelected("movie");
                match();
            }));
        }
        menu.add(groupLabel("Smart Mode"));
        menu.add(item("  Automatic", () -> {
            modeControl.setSelected("auto");
            match();
        }));
        menu.add(groupLabel("Local Mode"));
        menu.add(item("  Attributes (no network)", () -> {
            modeControl.setSelected("local");
            dbBox.setSelectedItem("local");
            match();
        }));
        menu.addSeparator();
        menu.add(item("Edit Format…", this::editFormat));
        menu.add(item("Preferences…", () -> buildSettingsPopup().show(namesList, 40, 40)));
        return menu;
    }

    private static JMenuItem groupLabel(String text) {
        JMenuItem label = new JMenuItem(text);
        label.setEnabled(false);
        return label;
    }

    private JPopupMenu buildEditPopup() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Edit Name…  (F2)", this::editName));
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
            rows.set(index, new Pair(pair.source(), target,
                    target.equals(pair.source()) ? STATUS_UNCHANGED : STATUS_RENAME));
            renameButton.setEnabled(hasPlanned());
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
    // Edit Format dialog (FileBot's FormatDialog)
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
        // FileBot's 14 example presets + a Jellyfin favourite, adapted to Renami formats
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

    /** F1–F12 apply presets 1–12, exactly like FileBot. */
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
    // History dialog (FileBot's Open History)
    // ============================================================

    private void openHistory() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"When", "Files", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        List<HistoryStore.Batch> batches = List.of();
        try {
            batches = history.batches();
            for (HistoryStore.Batch batch : batches.reversed()) {
                model.addRow(new Object[]{
                        java.time.LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(batch.time()), java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        batch.ops().size(),
                        batch.undone() ? "undone" : "applied"});
            }
        } catch (Exception e) {
            model.addRow(new Object[]{"history unavailable", "", ""});
        }
        JTable table = new JTable(model);
        Ui.styleTable(table);
        table.getColumnModel().getColumn(2).setCellRenderer(Ui.statusRenderer());
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(520, 300));
        JOptionPane.showMessageDialog(this, scroll, "Rename History", JOptionPane.PLAIN_MESSAGE);
    }

    // ============================================================
    // small button factories
    // ============================================================

    private static JButton iconButton(String icon, String tooltip, java.awt.event.ActionListener action) {
        JButton button = new JButton(Ui.icon(icon, 15));
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(4, 4, 4, 4));
        return button;
    }

    private static JButton textButton(String text, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.addActionListener(action);
        return button;
    }

    private static JButton popupButton(String icon, String tooltip,
                                       java.util.function.Supplier<JPopupMenu> menu) {
        JButton button = new JButton(Ui.icon(icon, 15));
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(4, 4, 4, 4));
        button.addActionListener(e -> menu.get().show(button, 0, button.getHeight()));
        return button;
    }
}
