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
import java.nio.file.Path;
import java.util.List;

/**
 * The rename panel, mirroring the classic media-renamer layout:
 * input row on top, old → new preview table in the middle, actions at the bottom.
 */
public final class RenamePanel extends JPanel {

    private final JTextField inputField = new JTextField(30);
    private final JComboBox<String> modeBox = new JComboBox<>(new String[]{"auto", "movie", "episode", "local"});
    private final JComboBox<String> dbBox = new JComboBox<>(new String[]{"auto", "tmdb", "tvdb", "omdb", "kitsu", "local"});
    private final JComboBox<String> actionBox = new JComboBox<>(new String[]{"rename", "move", "copy", "hardlink", "symlink"});
    private final JTextField formatField = new JTextField("{n} - {s00e00} - {t}", 24);
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
    private List<RenameController.Row> preview = List.of();

    public RenamePanel() {
        super(new BorderLayout(8, 8));

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.fill = GridBagConstraints.HORIZONTAL;
        addField(top, c, 0, "Input:", inputField, folderChooserButton());
        addField(top, c, 1, "Mode:", modeBox, null);
        addField(top, c, 2, "Database:", dbBox, null);
        addField(top, c, 3, "Action:", actionBox, null);
        addField(top, c, 4, "Format:", formatField, null);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(matchButton);
        bottom.add(renameButton);
        bottom.add(statusLabel);
        add(bottom, BorderLayout.SOUTH);

        matchButton.addActionListener(e -> match());
        renameButton.addActionListener(e -> rename());
        renameButton.setEnabled(false);
        FileDrop.register(this, this::onDrop);
    }

    /** Preload files opened from the command line or Finder. */
    public void preload(List<Path> dropped) {
        onDrop(dropped);
    }

    private void onDrop(List<Path> dropped) {
        if (dropped.size() == 1 && java.nio.file.Files.isDirectory(dropped.get(0))) {
            droppedFiles = List.of();
            inputField.setText(dropped.get(0).toString());
            statusLabel.setText("folder dropped");
        } else {
            droppedFiles = dropped;
            statusLabel.setText(dropped.size() + " files dropped");
        }
    }

    private JButton folderChooserButton() {
        JButton button = new JButton("…");
        button.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                inputField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        return button;
    }

    private static void addField(JPanel panel, GridBagConstraints c, int row,
                                 String label, JComponent field, JComponent trailing) {
        c.gridx = 0;
        c.gridy = row;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
        c.gridx = 2;
        c.weightx = 0;
        if (trailing != null) {
            panel.add(trailing, c);
        }
    }

    private void match() {
        Path input = Path.of(inputField.getText().trim());
        matchButton.setEnabled(false);
        statusLabel.setText("matching…");
        new SwingWorker<List<RenameController.Row>, Void>() {
            @Override
            protected List<RenameController.Row> doInBackground() throws Exception {
                files = droppedFiles.isEmpty() ? MediaScanner.scan(input) : droppedFiles;
                if (files.isEmpty()) {
                    throw new IllegalStateException("no media files found under " + input);
                }
                Datasource datasource = Datasources.create(Config.load(), (String) dbBox.getSelectedItem(), false);
                RenameController controller = new RenameController(datasource);
                return controller.preview(files, new RenameService.Options(
                        (String) modeBox.getSelectedItem(),
                        formatField.getText().isBlank() ? null : formatField.getText(),
                        0.60));
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
                TransferAction action = TransferAction.parse((String) actionBox.getSelectedItem());
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
