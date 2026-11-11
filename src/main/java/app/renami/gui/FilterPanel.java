package app.renami.gui;

import app.renami.cli.FilterCommand;
import app.renami.media.MediaScanner;
import app.renami.script.ScriptRuntime;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * FileBot's Filter mode: pick a folder, narrow it by type or a Groovy
 * predicate, then hand the selection to the Rename view.
 */
final class FilterPanel extends JPanel {

    private static final Map<String, String> TYPES = Map.of(
            "Video", "ext in ['mkv','mp4','avi','mov','wmv','flv','mpg','m4v','ts','webm']",
            "Audio", "ext in ['mp3','flac','m4a','aac','ogg','wav','opus']",
            "Images", "ext in ['jpg','jpeg','png','gif','bmp','webp','tiff']",
            "Archives", "ext in ['zip','rar','7z','tar','gz','iso']");

    private final JTextField folderField = new JTextField(28);
    private final JTextField predicateField = new JTextField("size > 0", 30);
    private final DefaultTableModel model =
            new DefaultTableModel(new Object[]{"Name", "Folder", "Size"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable table = new JTable(model);
    private final JLabel statusLabel = new JLabel("pick a folder, then filter it");
    private final List<Path> results = new ArrayList<>();
    private final RenamePanel renamePanel;
    private final Runnable showRename;

    private final JButton scanButton = Ui.prominent(new JButton("Scan"));

    JButton scanButton() {
        return scanButton;
    }

    FilterPanel(RenamePanel renamePanel, Runnable showRename) {
        super(new BorderLayout(0, 12));
        this.renamePanel = renamePanel;
        this.showRename = showRename;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        JLabel title = Ui.header("Filter");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        titleBar.add(title, BorderLayout.CENTER);
        top.add(titleBar);

        JPanel folderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        folderRow.setOpaque(false);
        folderRow.add(Ui.caption("Folder"));
        folderRow.add(folderField);
        JButton choose = new JButton("Choose…");
        choose.addActionListener(e -> chooseFolder());
        folderRow.add(choose);
        scanButton.addActionListener(e -> run());
        folderRow.add(scanButton);
        top.add(folderRow);

        JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chipRow.setOpaque(false);
        chipRow.add(Ui.caption("Quick filters"));
        TYPES.forEach((label, expr) -> {
            JButton chip = new JButton(label);
            chip.addActionListener(e -> {
                predicateField.setText(expr);
                run();
            });
            chipRow.add(chip);
        });
        top.add(chipRow);

        JPanel exprRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        exprRow.setOpaque(false);
        exprRow.add(Ui.caption("Groovy predicate (fn, name, ext, size, title, year, season, episode)"));
        predicateField.addActionListener(e -> run());
        exprRow.add(predicateField);
        JButton apply = new JButton("Apply");
        apply.addActionListener(e -> run());
        exprRow.add(apply);
        top.add(exprRow);
        add(top, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(Ui.caption("Matches"), BorderLayout.NORTH);
        JScrollPane scroller = new JScrollPane(table);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        body.add(scroller, BorderLayout.CENTER);
        add(Ui.card(body), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(false);
        statusLabel.setForeground(Ui.secondary());
        bottom.add(statusLabel, BorderLayout.CENTER);
        JButton send = new JButton("Send to Rename");
        send.addActionListener(e -> sendToRename());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(send);
        bottom.add(right, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        Ui.styleTable(table);
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            folderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void run() {
        String folder = folderField.getText().trim();
        if (folder.isEmpty() || !Files.isDirectory(Path.of(folder))) {
            statusLabel.setText("pick a real folder first");
            return;
        }
        statusLabel.setText("scanning…");
        new Thread(() -> {
            try {
                List<Path> files = MediaScanner.scan(Path.of(folder));
                Predicate<Map<String, Object>> predicate =
                        new ScriptRuntime(Map.of()).compilePredicate(predicateField.getText().trim());
                List<Path> matched = new ArrayList<>();
                for (Path file : files) {
                    try {
                        if (predicate.test(FilterCommand.bindingFor(file))) {
                            matched.add(file);
                        }
                    } catch (Exception ignored) {
                        // unreadable file: not a match
                    }
                }
                SwingUtilities.invokeLater(() -> show(matched, files.size()));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("filter failed: " + ex.getMessage()));
            }
        }).start();
    }

    private void show(List<Path> matched, int total) {
        results.clear();
        results.addAll(matched);
        model.setRowCount(0);
        for (Path p : matched) {
            long size = 0;
            try {
                size = Files.size(p);
            } catch (Exception ignored) {
            }
            model.addRow(new Object[]{p.getFileName().toString(),
                    p.getParent() == null ? "" : p.getParent().getFileName().toString(),
                    human(size)});
        }
        statusLabel.setText(matched.size() + " of " + total + " files matched");
    }

    private static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void sendToRename() {
        int[] rows = table.getSelectedRows();
        List<Path> selected = new ArrayList<>();
        if (rows.length == 0) {
            selected.addAll(results);
        } else {
            for (int row : rows) {
                selected.add(results.get(row));
            }
        }
        if (selected.isEmpty()) {
            statusLabel.setText("nothing to send");
            return;
        }
        renamePanel.preload(selected);
        showRename.run();
    }
}
