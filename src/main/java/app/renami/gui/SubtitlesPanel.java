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

/**
 * Subtitle view, FileBot's layout: search field + language + Login, a result
 * table with a filter field, and Download. Double-click a row to fetch it.
 */
public final class SubtitlesPanel extends JPanel {

    private static final String[] LANGUAGES = {
            "en", "de", "es", "fr", "it", "ja", "ko", "nl", "pl", "pt", "ru", "sv", "tr", "zh"};

    private final JTextField inputField = new JTextField(28);
    private final JComboBox<String> langBox = new JComboBox<>(LANGUAGES);
    private final JTextField filterField = new JTextField(18);
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
    private final JButton loginButton = new JButton("Login…");
    private final JLabel statusLabel = new JLabel(" ");

    private List<SubtitlesController.Row> preview = List.of();
    private List<SubtitlesController.Row> visible = List.of();
    private List<Path> droppedFiles = List.of();

    public SubtitlesPanel() {
        super(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        add(Ui.header("Subtitles"), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.add(Ui.card(buildOptions()), BorderLayout.NORTH);

        // FileBot's result tab: filter field + clear icon above the subtitle list
        JPanel filter = new JPanel(new BorderLayout(6, 0));
        filter.setOpaque(false);
        filter.add(Ui.caption("Filter"), BorderLayout.NORTH);
        JPanel filterRow = new JPanel(new BorderLayout(6, 0));
        filterRow.setOpaque(false);
        filterField.setToolTipText("Filter the subtitle list");
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                refilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                refilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                refilter();
            }
        });
        JButton clearFilter = new JButton(Ui.icon("exclude", 14));
        clearFilter.setToolTipText("Clear filter");
        clearFilter.setMargin(new Insets(2, 4, 2, 4));
        clearFilter.addActionListener(e -> filterField.setText(""));
        filterRow.add(filterField, BorderLayout.CENTER);
        filterRow.add(clearFilter, BorderLayout.EAST);
        filter.add(filterRow, BorderLayout.CENTER);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        Ui.styleTable(table);
        JPanel listCard = new JPanel(new BorderLayout(0, 8));
        listCard.setOpaque(false);
        listCard.add(filter, BorderLayout.NORTH);
        listCard.add(tableScroll, BorderLayout.CENTER);
        body.add(listCard, BorderLayout.CENTER);
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
        loginButton.addActionListener(e -> login());
        langBox.setEditable(true);

        // FileBot: double-click a subtitle row to download it
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0 && row < visible.size()) {
                        downloadOne(visible.get(row));
                    }
                }
            }
        });

        FileDrop.register(this, this::acceptFiles);
    }

    /** Cross-mode transfer: files dropped onto the rail land here. */
    public void acceptFiles(List<Path> dropped) {
        if (dropped.size() == 1 && Files.isDirectory(dropped.get(0))) {
            droppedFiles = List.of();
            inputField.setText(dropped.get(0).toString());
            statusLabel.setText("folder dropped");
        } else {
            droppedFiles = dropped;
            statusLabel.setText(dropped.size() + " files dropped");
        }
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
        c.gridx = 1; c.weightx = 0; c.insets = new Insets(2, 0, 2, 18);
        grid.add(field("Language", langBox), c);
        c.gridx = 2; c.weightx = 0; c.insets = new Insets(2, 0, 2, 0);
        grid.add(field("OpenSubtitles", loginButton), c);
        return grid;
    }

    private static JComponent field(String caption, JComponent control) {
        JPanel field = new JPanel(new BorderLayout(0, 4));
        field.setOpaque(false);
        field.add(Ui.caption(caption), BorderLayout.NORTH);
        field.add(control, BorderLayout.CENTER);
        return field;
    }

    /** FileBot's Login icon: store the OpenSubtitles API key locally. */
    private void login() {
        Config config = Config.load();
        String current = config.get("opensubtitles.api.key");
        JLabel hint = new JLabel(current == null
                ? "No API key configured. Get one at opensubtitles.com, then paste it below."
                : "A key is already configured. Replace it or leave blank to keep it.");
        hint.setForeground(Ui.secondary());
        JPasswordField keyField = new JPasswordField(24);
        JPanel form = new JPanel(new BorderLayout(0, 8));
        form.add(hint, BorderLayout.NORTH);
        form.add(keyField, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, form, "OpenSubtitles Login",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        String key = new String(keyField.getPassword()).trim();
        if (key.isEmpty()) {
            statusLabel.setText(current == null ? "no key entered" : "existing key kept");
            return;
        }
        Config.set("opensubtitles.api.key", key);
        statusLabel.setText("OpenSubtitles key saved to ~/.renami/config.properties");
    }

    private OpenSubtitlesClient createClient() {
        String key = Config.load().get("opensubtitles.api.key");
        if (key == null) {
            throw new IllegalStateException("no OpenSubtitles API key — press Login, or set "
                    + "RENAMI_OPENSUBTITLES_API_KEY / opensubtitles.api.key in ~/.renami/config.properties");
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
                return new SubtitlesController(createClient()).preview(files, langBox.getEditor().getItem().toString().trim());
            }

            @Override
            protected void done() {
                try {
                    preview = get();
                    refilter();
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

    /** Re-populate the table from the preview, honouring the filter field. */
    private void refilter() {
        String needle = filterField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        tableModel.setRowCount(0);
        visible = needle.isEmpty() ? preview : preview.stream()
                .filter(r -> (r.source().getFileName().toString() + " "
                        + r.target().getFileName().toString() + " "
                        + r.candidate().language()).toLowerCase(java.util.Locale.ROOT).contains(needle))
                .toList();
        for (SubtitlesController.Row row : visible) {
            tableModel.addRow(new Object[]{
                    row.source().getFileName(), row.target().getFileName(),
                    row.candidate().language(), row.candidate().downloads()});
        }
    }

    private void download() {
        if (preview.isEmpty()) {
            return;
        }
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

    /** Double-click on one row: download just that subtitle. */
    private void downloadOne(SubtitlesController.Row row) {
        downloadButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                new SubtitlesController(createClient()).download(row);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusLabel.setText("downloaded " + row.target().getFileName());
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
