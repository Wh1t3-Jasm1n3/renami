package app.renami.gui;

import app.renami.media.MediaScanner;
import app.renami.pipeline.RenameService;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.TransferAction;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.Datasources;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The rename view: drop zone on top, options and preview table in the middle,
 * one prominent action at the bottom — the FileBot flow, 2026-macOS style.
 */
public final class RenamePanel extends JPanel {

    private final SegmentedControl modeControl =
            new SegmentedControl("auto", "movie", "episode", "local");
    private final SegmentedControl actionControl =
            new SegmentedControl("rename", "move", "copy", "hardlink", "symlink");
    private final JComboBox<String> dbBox = new JComboBox<>(new String[]{"auto", "tmdb", "tvdb", "omdb", "kitsu", "local"});
    private final JTextField formatField = new JTextField("{n} - {s00e00} - {t}", 22);
    private final JCheckBox companionCheck = new JCheckBox("Companions", true);

    private final JLabel dropHint = new JLabel("Drop files or a folder here", SwingConstants.CENTER);
    private final JLabel dropSub = new JLabel("or pick a folder to scan", SwingConstants.CENTER);
    private final JButton chooseButton = new JButton("Choose…");
    private final DropZone dropZone = new DropZone();

    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[]{"File", "New Name", "Status"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable table = new JTable(tableModel);

    private final JButton matchButton = new JButton("Match");
    private final JButton renameButton = new JButton("Rename");
    private final JLabel statusLabel = new JLabel(" ");

    private List<Path> files = List.of();
    private List<Path> droppedFiles = List.of();
    private Path droppedFolder;
    private List<RenameController.Row> preview = List.of();

    public RenamePanel() {
        super(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildActionBar(), BorderLayout.SOUTH);

        chooseButton.addActionListener(e -> chooseFolder());
        matchButton.addActionListener(e -> match());
        renameButton.addActionListener(e -> rename());
        renameButton.setEnabled(false);
        FileDrop.register(this, this::onDrop);
    }

    /** Preload files opened from the command line or Finder. */
    public void preload(List<Path> dropped) {
        onDrop(dropped);
    }

    /** The primary action, so the frame can make it the default (Return) button. */
    JButton renameButton() {
        return renameButton;
    }

    // ---- layout ----

    private JComponent buildHeader() {
        JLabel title = new JLabel("Rename");
        title.setFont(Ui.title(22f));
        title.setForeground(Ui.text());
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        return header;
    }

    private JComponent buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.add(buildDropZone(), BorderLayout.NORTH);
        body.add(buildOptions(), BorderLayout.CENTER);
        return body;
    }

    private JComponent buildDropZone() {
        dropHint.setFont(Ui.title(15f));
        dropHint.setForeground(Ui.text());
        dropSub.setFont(Ui.body());
        dropSub.setForeground(Ui.secondary());
        chooseButton.setBorderPainted(false);
        chooseButton.setForeground(Ui.accent());
        chooseButton.setContentAreaFilled(false);
        chooseButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel text = new JPanel(new BorderLayout(2, 6));
        text.setOpaque(false);
        text.add(dropHint, BorderLayout.NORTH);
        text.add(dropSub, BorderLayout.CENTER);
        text.add(chooseButton, BorderLayout.SOUTH);

        JPanel inner = new JPanel(new BorderLayout(14, 0));
        inner.setOpaque(false);
        inner.add(new JLabel(Ui.icon("dropzone", 34)), BorderLayout.WEST);
        inner.add(text, BorderLayout.CENTER);

        dropZone.setLayout(new BorderLayout());
        dropZone.setOpaque(false);
        dropZone.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        dropZone.add(inner, BorderLayout.CENTER);
        new DropTarget(dropZone, new HoverDrop());
        return dropZone;
    }

    private JComponent buildOptions() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 18);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        c.gridx = 0; c.gridy = 0;
        grid.add(field("Mode", modeControl, false), c);
        c.gridx = 1; c.weightx = 1;
        grid.add(field("Action", actionControl, false), c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        grid.add(field("Database", dbBox, true), c);
        c.gridx = 1; c.gridy = 1; c.weightx = 1;
        grid.add(field("Format", formatField, true), c);
        c.gridx = 2; c.gridy = 1; c.weightx = 0; c.insets = new Insets(4, 0, 4, 0);
        JPanel extra = new JPanel(new BorderLayout(0, 16));
        extra.setOpaque(false);
        extra.add(Ui.caption(" "), BorderLayout.NORTH);
        extra.add(companionCheck, BorderLayout.CENTER);
        grid.add(extra, c);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(Ui.card(grid), BorderLayout.NORTH);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setDefaultRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable t, Object value, boolean sel,
                                                                    boolean focus, int row, int col) {
                java.awt.Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                ((javax.swing.JLabel) c).setHorizontalAlignment(LEFT);
                return c;
            }
        });
        table.getColumnModel().getColumn(2).setCellRenderer(Ui.statusRenderer());
        wrap.add(tableScroll, BorderLayout.CENTER);
        return wrap;
    }

    private static JComponent field(String caption, JComponent control, boolean stretch) {
        JPanel field = new JPanel(new BorderLayout(0, 4));
        field.setOpaque(false);
        field.add(Ui.caption(caption), BorderLayout.NORTH);
        field.add(control, stretch ? BorderLayout.CENTER : BorderLayout.WEST);
        return field;
    }

    private JComponent buildActionBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(matchButton);
        buttons.add(Ui.prominent(renameButton));
        bar.add(buttons, BorderLayout.WEST);
        statusLabel.setForeground(Ui.secondary());
        bar.add(statusLabel, BorderLayout.CENTER);
        return bar;
    }

    // ---- drop handling ----

    private void onDrop(java.util.List<Path> dropped) {
        if (dropped.size() == 1 && Files.isDirectory(dropped.get(0))) {
            droppedFiles = List.of();
            droppedFolder = dropped.get(0);
            showSelection(droppedFolder.getFileName() + " — folder");
        } else {
            droppedFiles = dropped;
            droppedFolder = null;
            showSelection(dropped.size() + " files");
        }
    }

    private void showSelection(String summary) {
        dropHint.setText(summary);
        dropHint.setForeground(Ui.accent());
        dropSub.setText("press Match to look them up");
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose a folder to scan");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            onDrop(List.of(chooser.getSelectedFile().toPath()));
        }
    }

    /** Drop target that lights the zone up while a drag hovers over it. */
    private final class HoverDrop implements DropTargetListener {
        @Override
        public void dragEnter(DropTargetDragEvent e) {
            dropZone.hovering = true;
            dropZone.repaint();
        }

        @Override
        public void dragExit(DropTargetEvent e) {
            dropZone.hovering = false;
            dropZone.repaint();
        }

        @Override
        public void dragOver(DropTargetDragEvent e) {
        }

        @Override
        public void dropActionChanged(DropTargetDragEvent e) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public void drop(DropTargetDropEvent e) {
            dropZone.hovering = false;
            try {
                e.acceptDrop(DnDConstants.ACTION_COPY);
                java.util.List<File> dropped = (java.util.List<File>) e.getTransferable()
                        .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                onDrop(dropped.stream().map(File::toPath).toList());
                e.dropComplete(true);
            } catch (Exception ex) {
                e.dropComplete(false);
            }
        }
    }

    /** Rounded dashed drop zone that fills with a soft accent tint on hover. */
    private static final class DropZone extends JPanel {
        boolean hovering;

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hovering) {
                Color accent = Ui.accent();
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Ui.dark() ? 40 : 22));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            }
            g2.setColor(hovering ? Ui.accent() : (Ui.dark() ? new Color(0x5A, 0x5E, 0x60) : new Color(0xC9, 0xC9, 0xD1)));
            g2.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND, 0, new float[]{6, 5}, 0));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.dispose();
        }
    }

    // ---- actions ----

    private void match() {
        if (droppedFolder == null && droppedFiles.isEmpty()) {
            statusLabel.setText("drop files or a folder first");
            return;
        }
        matchButton.setEnabled(false);
        statusLabel.setText("matching…");
        new SwingWorker<List<RenameController.Row>, Void>() {
            @Override
            protected List<RenameController.Row> doInBackground() throws Exception {
                files = droppedFiles.isEmpty() ? MediaScanner.scan(droppedFolder) : droppedFiles;
                if (files.isEmpty()) {
                    throw new IllegalStateException("no media files found under " + droppedFolder);
                }
                Datasource datasource = Datasources.create(Config.load(), (String) dbBox.getSelectedItem(), false);
                RenameController controller = new RenameController(datasource);
                return controller.preview(files, new RenameService.Options(
                        modeControl.getSelected(),
                        formatField.getText().isBlank() ? null : formatField.getText(),
                        0.60), companionCheck.isSelected());
            }

            @Override
            protected void done() {
                try {
                    preview = get();
                    tableModel.setRowCount(0);
                    for (RenameController.Row row : preview) {
                        tableModel.addRow(new Object[]{
                                row.source().getFileName(), row.target().getFileName(), row.status()});
                    }
                    statusLabel.setText(preview.stream().filter(r -> r.status().equals("rename")).count()
                            + " of " + preview.size() + " files will be renamed");
                    renameButton.setEnabled(preview.stream().anyMatch(r -> r.status().equals("rename")));
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("match failed");
                    JOptionPane.showMessageDialog(RenamePanel.this, cause.getMessage(), "Match failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    matchButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void rename() {
        int confirmed = JOptionPane.showConfirmDialog(this,
                "Rename " + preview.stream().filter(r -> r.status().equals("rename")).count() + " files?",
                "Confirm rename", JOptionPane.YES_NO_OPTION);
        if (confirmed != JOptionPane.YES_OPTION) {
            return;
        }
        renameButton.setEnabled(false);
        new SwingWorker<List<RenameController.Row>, Void>() {
            @Override
            protected List<RenameController.Row> doInBackground() throws Exception {
                TransferAction action = TransferAction.parse(actionControl.getSelected());
                RenameController controller = new RenameController(new app.renami.web.local.LocalDatasource());
                List<RenameController.Row> applied = controller.apply(preview, ConflictPolicy.FAIL, action);
                new HistoryStore().record(applied.stream()
                        .filter(RenameController.Row::applied)
                        .map(r -> new app.renami.rename.RenameOperation(r.source(), r.target()))
                        .toList(), action);
                return applied;
            }

            @Override
            protected void done() {
                try {
                    List<RenameController.Row> applied = get();
                    tableModel.setRowCount(0);
                    for (RenameController.Row row : applied) {
                        tableModel.addRow(new Object[]{
                                row.source().getFileName(), row.target().getFileName(), row.status()});
                    }
                    long done = applied.stream().filter(RenameController.Row::applied).count();
                    statusLabel.setText(done + " files processed. Use `renami undo` to revert.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("rename failed");
                    JOptionPane.showMessageDialog(RenamePanel.this, cause.getMessage(), "Rename failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    renameButton.setEnabled(true);
                }
            }
        }.execute();
    }
}
