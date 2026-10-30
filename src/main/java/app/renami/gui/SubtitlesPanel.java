package app.renami.gui;

import app.renami.media.MediaScanner;
import app.renami.util.Config;
import app.renami.web.HttpJson;
import app.renami.web.opensubtitles.OpenSubtitlesClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

/** Subtitle panel: pick a folder, search matches, download on confirm. */
public final class SubtitlesPanel extends JPanel {

    private final JTextField inputField = new JTextField(30);
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

    public SubtitlesPanel() {
        super(new BorderLayout(8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Input:"));
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
        top.add(new JLabel("Language:"));
        top.add(langField);
        add(top, BorderLayout.NORTH);

        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(searchButton);
        bottom.add(downloadButton);
        bottom.add(statusLabel);
        add(bottom, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> search());
        downloadButton.addActionListener(e -> download());
        downloadButton.setEnabled(false);
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
                List<Path> files = MediaScanner.scan(input);
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
