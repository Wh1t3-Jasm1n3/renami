package app.renami.gui;

import app.renami.cli.DupeCommand;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Duplicate finder view: scan a folder, show duplicate groups side by side. */
public final class DupePanel extends JPanel {

    private final JTextField inputField = new JTextField(28);
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
        super(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));


        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.add(Ui.card(buildOptions()), BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        Ui.styleTable(table);
        body.add(tableScroll, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(Ui.prominent(findButton));
        bar.add(buttons, BorderLayout.WEST);
        statusLabel.setForeground(Ui.secondary());
        bar.add(statusLabel, BorderLayout.CENTER);
        add(bar, BorderLayout.SOUTH);

        findButton.addActionListener(e -> find());
        FileDrop.register(this, this::acceptFiles);
    }

    /** Cross-mode transfer: files dropped onto the rail land here. */
    public void acceptFiles(List<Path> dropped) {
        if (dropped.size() == 1 && Files.isDirectory(dropped.get(0))) {
            inputField.setText(dropped.get(0).toString());
            statusLabel.setText("folder dropped");
        } else {
            statusLabel.setText("drop a folder to scan for duplicates");
        }
    }

    /** The primary action, so the frame can make it the default (Return) button. */
    JButton findButton() {
        return findButton;
    }

    private JComponent buildOptions() {
        JPanel folder = new JPanel(new BorderLayout(8, 0));
        folder.setOpaque(false);
        folder.add(inputField, BorderLayout.CENTER);
        JButton browse = new JButton("Choose…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                inputField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        folder.add(browse, BorderLayout.EAST);

        JPanel field = new JPanel(new BorderLayout(0, 4));
        field.setOpaque(false);
        field.add(Ui.caption("Folder"), BorderLayout.NORTH);
        field.add(folder, BorderLayout.CENTER);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        grid.add(field, c);
        return grid;
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
