package app.renami.gui;

import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.Datasources;
import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.ShowInfo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * FileBot's Episodes mode: search a series on TheTVDB/TMDb, browse a season's
 * episode list, then copy the names or hand them to the Rename view.
 */
final class EpisodesPanel extends JPanel {

    private final JComboBox<String> dbBox = new JComboBox<>(new String[]{"tvdb", "tmdb"});
    private final JTextField searchField = new JTextField(24);
    private final DefaultTableModel showsModel =
            new DefaultTableModel(new Object[]{"Series", "Year"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable showsTable = new JTable(showsModel);
    private final JSpinner seasonSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 99, 1));
    private final DefaultTableModel episodesModel =
            new DefaultTableModel(new Object[]{"#", "Episode", "Title", "Air date"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable episodesTable = new JTable(episodesModel);
    private final JLabel statusLabel = new JLabel("search a series to list its episodes");
    private final AtomicReference<List<ShowInfo>> shows = new AtomicReference<>(List.of());
    private final AtomicReference<List<EpisodeInfo>> episodes = new AtomicReference<>(List.of());

    private final RenamePanel renamePanel;
    private final Runnable showRename;

    private final JButton searchButton = Ui.prominent(new JButton("Search"));

    JButton searchButton() {
        return searchButton;
    }

    EpisodesPanel(RenamePanel renamePanel, Runnable showRename) {
        super(new BorderLayout(0, 12));
        this.renamePanel = renamePanel;
        this.showRename = showRename;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        JLabel title = Ui.header("Episodes");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        titleBar.add(title, BorderLayout.CENTER);
        top.add(titleBar, BorderLayout.NORTH);
        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        search.setOpaque(false);
        search.add(Ui.caption("Source"));
        search.add(dbBox);
        searchField.addActionListener(e -> search());
        search.add(searchField);
        searchButton.addActionListener(e -> search());
        search.add(searchButton);
        top.add(search, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel split = new JPanel(new BorderLayout(12, 0));
        split.setOpaque(false);
        split.add(card("Series", showsTable), BorderLayout.WEST);
        split.add(card("Episodes", episodesTable), BorderLayout.CENTER);
        add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(false);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.setOpaque(false);
        tools.add(Ui.caption("Season"));
        tools.add(seasonSpinner);
        JButton load = new JButton("Load season");
        load.addActionListener(e -> loadSeason());
        tools.add(load);
        JButton copy = new JButton("Copy to Clipboard");
        copy.addActionListener(e -> copyNames());
        JButton send = new JButton("Send to Rename");
        send.addActionListener(e -> sendToRename());
        bottom.add(tools, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(copy);
        right.add(send);
        bottom.add(right, BorderLayout.EAST);
        statusLabel.setForeground(Ui.secondary());
        bottom.add(statusLabel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        Ui.styleTable(showsTable);
        Ui.styleTable(episodesTable);
        showsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSeason();
            }
        });
    }

    private JComponent card(String title, JTable table) {
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(Ui.caption(title), BorderLayout.NORTH);
        JScrollPane scroller = new JScrollPane(table);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        body.add(scroller, BorderLayout.CENTER);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(Ui.card(body), BorderLayout.CENTER);
        wrapper.setPreferredSize(new Dimension(title.equals("Series") ? 300 : 520, 0));
        return wrapper;
    }

    private void search() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            statusLabel.setText("type a series name first");
            return;
        }
        statusLabel.setText("searching " + dbBox.getSelectedItem() + "…");
        new Thread(() -> {
            try {
                Datasource db = Datasources.create(Config.load(), (String) dbBox.getSelectedItem(), false);
                List<ShowInfo> found = db.searchShow(query, 0);
                SwingUtilities.invokeLater(() -> {
                    shows.set(found);
                    showsModel.setRowCount(0);
                    for (ShowInfo s : found) {
                        showsModel.addRow(new Object[]{s.name(), s.year() > 0 ? s.year() : ""});
                    }
                    statusLabel.setText(found.size() + " series found — pick one to list episodes");
                    if (!found.isEmpty()) {
                        showsTable.setRowSelectionInterval(0, 0);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("search failed: " + ex.getMessage()));
            }
        }).start();
    }

    private void loadSeason() {
        int row = showsTable.getSelectedRow();
        if (row < 0 || row >= shows.get().size()) {
            return;
        }
        ShowInfo show = shows.get().get(row);
        int season = (Integer) seasonSpinner.getValue();
        statusLabel.setText("loading " + show.name() + " S" + season + "…");
        new Thread(() -> {
            try {
                Datasource db = Datasources.create(Config.load(), (String) dbBox.getSelectedItem(), false);
                List<EpisodeInfo> found = db.getSeason(show.id(), season);
                SwingUtilities.invokeLater(() -> {
                    episodes.set(found);
                    episodesModel.setRowCount(0);
                    for (EpisodeInfo ep : found) {
                        episodesModel.addRow(new Object[]{
                                ep.episode(),
                                String.format("S%02dE%02d", ep.season(), ep.episode()),
                                ep.name() == null ? "" : ep.name(),
                                ep.airDate() == null ? "" : ep.airDate()});
                    }
                    statusLabel.setText(found.size() + " episodes in " + show.name() + " S" + season);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("load failed: " + ex.getMessage()));
            }
        }).start();
    }

    private String namesJoined() {
        StringBuilder sb = new StringBuilder();
        for (EpisodeInfo ep : episodes.get()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(String.format("S%02dE%02d", ep.season(), ep.episode()));
            if (ep.name() != null && !ep.name().isBlank()) {
                sb.append(" - ").append(ep.name());
            }
        }
        return sb.toString();
    }

    private void copyNames() {
        String text = namesJoined();
        if (text.isEmpty()) {
            statusLabel.setText("no episodes loaded");
            return;
        }
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
        statusLabel.setText(episodes.get().size() + " episode names copied to clipboard");
    }

    private void sendToRename() {
        String text = namesJoined();
        if (text.isEmpty()) {
            statusLabel.setText("no episodes loaded");
            return;
        }
        copyNames();
        renamePanel.loadNamesFromText(text);
        showRename.run();
    }
}
