package app.renami.gui;

import app.renami.media.MediaScanner;
import app.renami.util.Config;
import app.renami.web.HttpJson;
import app.renami.web.opensubtitles.OpenSubtitlesClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Subtitle view: pick a folder, search matches, download on confirm. */
public final class SubtitlesPanel extends JPanel {

    private final JTextField inputField = new JTextField(28);
    private final JTextField langField = new JTextField("en", 6);
    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[]{"File", "Subtitle", "Language", "Downloads"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable table = new JTable(tableModel);
    private final JButton searchButton = new JButton("Search");
    private final JButton downloadButton = new JButton("Download");
    private final JLabel statusLabel = new JLabel(" ");

    private List<SubtitlesController.Row> preview = List.of();
    private List<Path> droppedFiles = List.of();

    public SubtitlesPanel() {
        super(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        add(Ui.header("Subtitles"), BorderLayout.NORTH);

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
        buttons.add(searchButton);
        buttons.add(Ui.prominent(downloadButton));
        bar.add(buttons, BorderLayout.WEST);
        statusLabel.setForeground(Ui.secondary());
        bar.add(statusLabel, BorderLayout.CENTER);
        add(bar, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> search());
        downloadButton.addActionListener(e -> download());
        downloadButton.setEnabled(false);
        FileDrop.register(this, dropped -> {
            if (dropped.size() == 1 && Files.isDirectory(dropped.get(0))) {
                droppedFiles = List.of();
                inputField.setText(dropped.get(0).toString());
                statusLabel.setText("folder dropped");
            } else {
                droppedFiles = dropped;
                statusLabel.setText(dropped.size() + " files dropped");
            }
        });
    }

    /** The primary action, so the frame can make it the default (Return) button. */
    JButton downloadButton() {
        return downloadButton;
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

        c.gridx = 0; c.weightx = 1;
        grid.add(field("Folder", folder), c);
        c.gridx = 1; c.weightx = 0; c.insets = new Insets(2, 0, 2, 0);
        grid.add(field("Language", langField), c);
        return grid;
    }

    private static JComponent field(String caption, JComponent control) {
        JPanel field = new JPanel(new BorderLayout(0, 4));
        field.setOpaque(false);
        field.add(Ui.caption(caption), BorderLayout.NORTH);
        field.add(control, BorderLayout.CENTER);
        return field;
    }

    private OpenSubtitlesClient createClient() {
        String key = Config.load().get("opensubtitles.api.key");
        if (key == null) {
            throw new IllegalStateException("no OpenSubtitles API key — set RENAMI_OPENSUBTITLES_API_KEY "
                    + "or opensubtitles.api.key in ~/.renami/config.properties");
        }
        return new OpenSubtitlesClient(key, new HttpJson(Config.dataDir().resolve("cache"), true));
    }

    private void search() {
        Path input = Path.of(inputField.getText().trim());
        searchButton.setEnabled(false);
        statusLabel.setText("searching…");
        new SwingWorker<List<SubtitlesController.Row>, Void>() {
            @Override
            protected List<SubtitlesController.Row> doInBackground() throws Exception {
                List<Path> files = droppedFiles.isEmpty() ? MediaScanner.scan(input) : droppedFiles;
                if (files.isEmpty()) {
                    throw new IllegalStateException("no media files found under " + input);
                }
                return new SubtitlesController(createClient()).preview(files, langField.getText().trim());
            }

            @Override
            protected void done() {
                try {
                    preview = get();
                    tableModel.setRowCount(0);
                    for (SubtitlesController.Row row : preview) {
                        tableModel.addRow(new Object[]{
                                row.source().getFileName(), row.target().getFileName(),
                                row.candidate().language(), row.candidate().downloads()});
                    }
                    statusLabel.setText(preview.size() + " subtitle matches found");
                    downloadButton.setEnabled(!preview.isEmpty());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("search failed");
                    JOptionPane.showMessageDialog(SubtitlesPanel.this, cause.getMessage(),
                            "Subtitle search failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    searchButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void download() {
        int confirmed = JOptionPane.showConfirmDialog(this,
                "Download " + preview.size() + " subtitle files?", "Confirm download", JOptionPane.YES_NO_OPTION);
        if (confirmed != JOptionPane.YES_OPTION) {
            return;
        }
        downloadButton.setEnabled(false);
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                SubtitlesController controller = new SubtitlesController(createClient());
                int saved = 0;
                Exception firstError = null;
                for (SubtitlesController.Row row : preview) {
                    try {
                        controller.download(row);
                        saved++;
                    } catch (Exception e) {
                        if (firstError == null) {
                            firstError = e;
                        }
                    }
                }
                if (saved == 0 && firstError != null) {
                    throw firstError;
                }
                return saved;
            }

            @Override
            protected void done() {
                try {
                    statusLabel.setText(get() + " subtitles downloaded");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("download failed");
                    JOptionPane.showMessageDialog(SubtitlesPanel.this, cause.getMessage(),
                            "Download failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    downloadButton.setEnabled(true);
                }
            }
        }.execute();
    }
}
