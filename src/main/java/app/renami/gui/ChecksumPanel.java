package app.renami.gui;

import app.renami.hash.HashType;
import app.renami.hash.VerificationFile;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Checksum view: hash a folder, save a sidecar verification file (SFV/MD5/…),
 * and verify files against one — OK / MISMATCH / MISSING at a glance.
 */
public final class ChecksumPanel extends JPanel {

    private final SegmentedControl algoControl = new SegmentedControl("crc32", "md5", "ed2k", "sha1", "sha256", "sha512");
    private final JTextField inputField = new JTextField(28);
    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[]{"File", "Hash", "Status"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable table = new JTable(tableModel);
    private final JButton generateButton = new JButton("Generate");
    private final JButton verifyButton = new JButton("Verify…");
    private final JButton saveButton = new JButton("Save sidecar…");
    private final JLabel statusLabel = new JLabel(" ");

    private Path input;
    private List<VerificationFile.Entry> entries = List.of();

    public ChecksumPanel() {
        super(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        add(Ui.header("Checksum"), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.add(Ui.card(buildOptions()), BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        Ui.styleTable(table);
        table.getColumnModel().getColumn(2).setCellRenderer(Ui.statusRenderer());
        // SFV table: Delete removes the selected rows
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "deleteRows");
        table.getActionMap().put("deleteRows", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int[] selected = table.getSelectedRows();
                for (int i = selected.length - 1; i >= 0; i--) {
                    tableModel.removeRow(selected[i]);
                }
                // keep the in-memory entries in sync with what's visible
                List<VerificationFile.Entry> kept = new ArrayList<>();
                for (int row = 0; row < tableModel.getRowCount(); row++) {
                    kept.add(new VerificationFile.Entry(
                            String.valueOf(tableModel.getValueAt(row, 0)),
                            String.valueOf(tableModel.getValueAt(row, 1))));
                }
                entries = kept;
                saveButton.setEnabled(!entries.isEmpty());
                statusLabel.setText(entries.size() + " entries");
            }
        });
        body.add(tableScroll, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(Ui.prominent(generateButton));
        buttons.add(verifyButton);
        buttons.add(saveButton);
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> {
            tableModel.setRowCount(0);
            entries = List.of();
            saveButton.setEnabled(false);
            statusLabel.setText("cleared");
        });
        buttons.add(clearButton);
        bar.add(buttons, BorderLayout.WEST);
        statusLabel.setForeground(Ui.secondary());
        bar.add(statusLabel, BorderLayout.CENTER);
        add(bar, BorderLayout.SOUTH);

        generateButton.addActionListener(e -> generate());
        verifyButton.addActionListener(e -> verify());
        saveButton.addActionListener(e -> saveSidecar());
        saveButton.setEnabled(false);
        FileDrop.register(this, this::acceptFiles);
    }

    /** Cross-mode transfer: files dropped onto the rail land here. */
    public void acceptFiles(List<Path> dropped) {
        if (dropped.size() == 1) {
            input = dropped.get(0);
            inputField.setText(input.toString());
            statusLabel.setText("press Generate to hash, or Verify against a sidecar");
        }
    }

    /** The primary action, so the frame can make it the default (Return) button. */
    JButton generateButton() {
        return generateButton;
    }

    private JComponent buildOptions() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 0, 2, 18);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        JPanel folder = new JPanel(new BorderLayout(8, 0));
        folder.setOpaque(false);
        JButton browse = new JButton("Choose…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                input = chooser.getSelectedFile().toPath();
                inputField.setText(input.toString());
            }
        });
        folder.add(inputField, BorderLayout.CENTER);
        folder.add(browse, BorderLayout.EAST);

        c.gridx = 0; c.weightx = 1;
        grid.add(field("Folder or file", folder), c);
        c.gridx = 1; c.weightx = 0; c.insets = new Insets(2, 0, 2, 0);
        grid.add(field("Algorithm", algoControl), c);
        return grid;
    }

    private static JComponent field(String caption, JComponent control) {
        JPanel field = new JPanel(new BorderLayout(0, 4));
        field.setOpaque(false);
        field.add(Ui.caption(caption), BorderLayout.NORTH);
        field.add(control, BorderLayout.CENTER);
        return field;
    }

    private void generate() {
        if (input == null) {
            statusLabel.setText("pick a folder or file first");
            return;
        }
        HashType type = HashType.parse(algoControl.getSelected());
        generateButton.setEnabled(false);
        saveButton.setEnabled(false);
        statusLabel.setText("hashing…");
        new SwingWorker<List<VerificationFile.Entry>, Void>() {
            @Override
            protected List<VerificationFile.Entry> doInBackground() throws Exception {
                List<VerificationFile.Entry> result = new ArrayList<>();
                for (Path file : filesToHash(input)) {
                    String relative = Files.isDirectory(input)
                            ? input.relativize(file).toString()
                            : file.getFileName().toString();
                    result.add(new VerificationFile.Entry(relative, type.hex(file)));
                    statusLabel.setText("hashed " + relative);
                }
                return result;
            }

            @Override
            protected void done() {
                try {
                    entries = get();
                    tableModel.setRowCount(0);
                    for (VerificationFile.Entry entry : entries) {
                        tableModel.addRow(new Object[]{entry.name(), entry.hash(), "ok"});
                    }
                    statusLabel.setText(entries.size() + " files hashed with " + type.name().toLowerCase());
                    saveButton.setEnabled(!entries.isEmpty());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("hashing failed");
                    JOptionPane.showMessageDialog(ChecksumPanel.this, cause.getMessage(),
                            "Hashing failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    generateButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void verify() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a sidecar file (.sfv, .md5, .sha…)");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path sidecar = chooser.getSelectedFile().toPath();
        verifyButton.setEnabled(false);
        statusLabel.setText("verifying…");
        new SwingWorker<int[], Void>() {
            @Override
            protected int[] doInBackground() throws Exception {
                List<VerificationFile.Entry> expected = VerificationFile.read(sidecar);
                if (expected.isEmpty()) {
                    throw new IllegalStateException("no entries found in " + sidecar);
                }
                Path base = sidecar.toAbsolutePath().getParent();
                HashType declared = VerificationFile.readAlgorithm(sidecar);
                int ok = 0, bad = 0;
                tableModel.setRowCount(0);
                for (VerificationFile.Entry entry : expected) {
                    HashType type = declared != null ? declared : HashType.byHexLength(entry.hash().length());
                    Path file = base.resolve(entry.name()).normalize();
                    String status;
                    if (type == null) {
                        status = "unknown algo";
                    } else if (!Files.isRegularFile(file)) {
                        status = "missing";
                    } else {
                        status = type.hex(file).equals(entry.hash()) ? "ok" : "MISMATCH";
                    }
                    if (status.equals("ok")) {
                        ok++;
                    } else {
                        bad++;
                    }
                    String name = entry.name();
                    String hash = entry.hash();
                    SwingUtilities.invokeLater(() ->
                            tableModel.addRow(new Object[]{name, hash, status}));
                }
                return new int[]{ok, bad};
            }

            @Override
            protected void done() {
                try {
                    int[] counts = get();
                    statusLabel.setText(counts[0] + " ok, " + counts[1] + " problems");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("verify failed");
                    JOptionPane.showMessageDialog(ChecksumPanel.this, cause.getMessage(),
                            "Verify failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    verifyButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void saveSidecar() {
        if (entries.isEmpty()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save sidecar verification file");
        chooser.setSelectedFile(new java.io.File(input != null
                ? input.resolve("checksums.sha256").toFile().getAbsolutePath()
                : "checksums.sha256"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        try {
            HashType type = VerificationFile.defaultAlgorithm(target, HashType.parse(algoControl.getSelected()));
            VerificationFile.write(target, entries, type);
            statusLabel.setText(entries.size() + " entries written to " + target.getFileName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(ChecksumPanel.this, ex.getMessage(),
                    "Write failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static List<Path> filesToHash(Path input) throws Exception {
        if (Files.isRegularFile(input)) {
            return List.of(input);
        }
        try (Stream<Path> stream = Files.walk(input)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
