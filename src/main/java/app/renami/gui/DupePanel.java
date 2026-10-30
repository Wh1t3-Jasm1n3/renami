package app.renami.gui;

import app.renami.cli.DupeCommand;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Duplicate finder panel: scan a folder, show duplicate groups side by side. */
public final class DupePanel extends JPanel {

    private final JTextField inputField = new JTextField(30);
    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[]{"Group", "File", "Size"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable table = new JTable(tableModel);
    private final JButton findButton = new JButton("Find duplicates");
    private final JLabel statusLabel = new JLabel(" ");

    public DupePanel() {
        super(new BorderLayout(8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Folder:"));
        top.add(inputField, "width 350:350:350");
        JButton browse = new JButton("…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                inputField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        top.add(browse);
        add(top, BorderLayout.NORTH);

        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(findButton);
        bottom.add(statusLabel);
        add(bottom, BorderLayout.SOUTH);

        findButton.addActionListener(e -> find());
        FileDrop.register(this, dropped -> {
            if (dropped.size() == 1 && Files.isDirectory(dropped.get(0))) {
                inputField.setText(dropped.get(0).toString());
                statusLabel.setText("folder dropped");
            } else {
                statusLabel.setText("drop a folder to scan for duplicates");
            }
        });
    }

    private void find() {
        Path input = Path.of(inputField.getText().trim());
        findButton.setEnabled(false);
        statusLabel.setText("scanning…");
        new SwingWorker<List<List<Path>>, Void>() {
            @Override
            protected List<List<Path>> doInBackground() throws IOException {
                if (!Files.isDirectory(input)) {
                    throw new IllegalStateException("not a folder: " + input);
                }
                return DupeCommand.findDuplicates(input);
            }

            @Override
            protected void done() {
                try {
                    List<List<Path>> groups = get();
                    tableModel.setRowCount(0);
                    int index = 1;
                    for (List<Path> group : groups) {
                        for (Path p : group) {
                            tableModel.addRow(new Object[]{"#" + index, p, Files.size(p)});
                        }
                        index++;
                    }
                    statusLabel.setText(groups.isEmpty() ? "no duplicates found"
                            : groups.size() + " duplicate groups");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("scan failed");
                    JOptionPane.showMessageDialog(DupePanel.this, cause.getMessage(),
                            "Duplicate scan failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    findButton.setEnabled(true);
                }
            }
        }.execute();
    }
}
