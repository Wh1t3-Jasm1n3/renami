package app.renami.gui;

import app.renami.media.MediaScanner;
import app.renami.parse.FileNameParser;
import app.renami.parse.ReleaseInfo;
import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.RenameExecutor;
import app.renami.rename.RenameOperation;
import app.renami.rename.TransferAction;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.Datasources;
import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.ShowInfo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Episodes mode: the show's episode guide is the source of truth.
 * Pick the series and season, drop your (badly named) files onto the guide,
 * and Renami attaches each file to its episode — by SxxEyy, by bare number,
 * by air date, or by order — then renames them in place. The Rename view
 * starts from the files instead; this view starts from the guide.
 */
final class EpisodesPanel extends JPanel {

    /** yyyy-MM-dd (also 2012.03.16 / 2012-03-16 style dates inside names). */
    private static final Pattern DATE = Pattern.compile("(\\d{4})[-.](\\d{2})[-.](\\d{2})");
    /** Placeholder titles from the datasource carry no information. */
    private static final Pattern JUNK_TITLE = Pattern.compile("(?i)episode \\d+");

    private final JComboBox<String> dbBox = new JComboBox<>(new String[]{"auto", "tmdb", "tvmaze", "kitsu"});
    private final JComboBox<String> orderBox = new JComboBox<>(new String[]{"default", "aired", "dvd", "absolute"});
    private final JComboBox<String> langBox = new JComboBox<>(
            new String[]{"en", "de", "es", "fr", "it", "ja", "ko", "nl", "pl", "pt", "ru", "sv", "tr", "zh"});
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
            new DefaultTableModel(new Object[]{"#", "Episode", "Title", "Air date", "Your file"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable episodesTable = new JTable(episodesModel);
    private final JLabel statusLabel = new JLabel("search a series, pick a season, then drop your files on the list");
    private final AtomicReference<List<ShowInfo>> shows = new AtomicReference<>(List.of());
    private final List<EpisodeInfo> episodes = new ArrayList<>();
    /** Parallel to episodes: the file attached to each guide row, or null. */
    private final List<Path> attached = new ArrayList<>();
    private ShowInfo currentShow;

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
        split.add(card("Episode guide — drop your files here", episodesTable), BorderLayout.CENTER);
        add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(false);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.setOpaque(false);
        tools.add(Ui.caption("Season"));
        tools.add(seasonSpinner);
        tools.add(Ui.caption("Order"));
        tools.add(orderBox);
        tools.add(Ui.caption("Language"));
        tools.add(langBox);
        langBox.setEditable(true);
        String savedLang = Config.load().get("datasource.language");
        if (savedLang != null) {
            langBox.getEditor().setItem(savedLang);
        }
        langBox.addActionListener(e -> Config.set("datasource.language",
                langBox.getEditor().getItem().toString().trim()));
        JButton load = new JButton("Load season");
        load.addActionListener(e -> loadSeason());
        tools.add(load);
        JButton add = new JButton("Add files…");
        add.addActionListener(e -> chooseFiles());
        tools.add(add);
        JButton copy = new JButton("Copy");
        copy.addActionListener(e -> copyNames());
        JButton save = new JButton("Save…");
        save.addActionListener(e -> saveNames());
        JButton send = new JButton("Send to Rename");
        send.addActionListener(e -> sendToRename());
        JButton rename = Ui.prominent(new JButton("Rename attached"));
        rename.addActionListener(e -> renameAttached());
        bottom.add(tools, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(copy);
        right.add(save);
        right.add(send);
        right.add(rename);
        bottom.add(right, BorderLayout.EAST);
        JPanel bottomStack = new JPanel(new BorderLayout(0, 4));
        bottomStack.setOpaque(false);
        bottomStack.add(bottom, BorderLayout.NORTH);
        statusLabel.setForeground(Ui.secondary());
        bottomStack.add(statusLabel, BorderLayout.SOUTH);
        add(bottomStack, BorderLayout.SOUTH);

        Ui.styleTable(showsTable);
        Ui.styleTable(episodesTable);
        showsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSeason();
            }
        });

        // Drop files straight onto the guide: each file is attached to its episode.
        episodesTable.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDrop()
                        && support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    List<java.io.File> files = (List<java.io.File>) support.getTransferable()
                            .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    attachFiles(expand(files.stream().map(java.io.File::toPath).toList()));
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });

        // Guide row context menu: attach / detach / Send to / Copy / Save / Select All / Delete
        episodesTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                maybeShowMenu(e);
            }

            private void maybeShowMenu(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = episodesTable.rowAtPoint(e.getPoint());
                if (row >= 0 && !episodesTable.isRowSelected(row)) {
                    episodesTable.setRowSelectionInterval(row, row);
                }
                javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
                if (row >= 0) {
                    menu.add(menuItem("Attach file…", () -> attachOne(row)));
                    menu.add(menuItem("Detach file", () -> {
                        setAttached(row, null);
                        statusLabel.setText("detached " + episodes.get(row).name());
                    }));
                    menu.addSeparator();
                }
                menu.add(menuItem("Send to Rename", EpisodesPanel.this::sendToRename));
                menu.add(menuItem("Copy", EpisodesPanel.this::copyNames));
                menu.add(menuItem("Save…", EpisodesPanel.this::saveNames));
                menu.addSeparator();
                menu.add(menuItem("Select All", () ->
                        episodesTable.setRowSelectionInterval(0, episodesTable.getRowCount() - 1)));
                menu.add(menuItem("Delete", () -> {
                    int[] selected = episodesTable.getSelectedRows();
                    for (int i = selected.length - 1; i >= 0; i--) {
                        if (selected[i] < episodes.size()) {
                            episodes.remove(selected[i]);
                            attached.remove(selected[i]);
                        }
                    }
                    refreshGuideTable();
                    statusLabel.setText(episodes.size() + " episodes");
                }));
                menu.show(episodesTable, e.getX(), e.getY());
            }
        });
    }

    private static javax.swing.JMenuItem menuItem(String text, Runnable action) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(text);
        item.addActionListener(e -> action.run());
        return item;
    }

    /** Save button: export the episode list to a text file. */
    private void saveNames() {
        String text = namesJoined();
        if (text.isEmpty()) {
            statusLabel.setText("no episodes loaded");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save episode list");
        chooser.setSelectedFile(new java.io.File("episodes.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Files.writeString(chooser.getSelectedFile().toPath(), text + System.lineSeparator());
            statusLabel.setText("saved " + episodes.size() + " episodes to "
                    + chooser.getSelectedFile().getName());
        } catch (java.io.IOException ex) {
            statusLabel.setText("save failed: " + ex.getMessage());
        }
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
        wrapper.setPreferredSize(new Dimension(title.startsWith("Series") ? 300 : 520, 0));
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
                String order = (String) orderBox.getSelectedItem();
                String language = langBox.getEditor().getItem().toString().trim();
                List<EpisodeInfo> found = db.getSeason(show.id(), season, order, language.isEmpty() ? null : language);
                SwingUtilities.invokeLater(() -> {
                    currentShow = show;
                    episodes.clear();
                    episodes.addAll(found);
                    attached.clear();
                    refreshGuideTable();
                    statusLabel.setText(found.size() + " episodes in " + show.name() + " S" + season
                            + " — drop your files on the list");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("load failed: " + ex.getMessage()));
            }
        }).start();
    }

    // ---------------------------------------------------------------- attach

    /** "Add files…" button: pick files or folders, then auto-attach them. */
    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Add episode files");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        List<Path> picked = new ArrayList<>();
        for (java.io.File f : chooser.getSelectedFiles()) {
            picked.add(f.toPath());
        }
        attachFiles(expand(picked));
    }

    /** Expands dropped folders into their video files. */
    private static List<Path> expand(List<Path> picked) {
        List<Path> files = new ArrayList<>();
        for (Path p : picked) {
            if (Files.isDirectory(p)) {
                try {
                    MediaScanner.scan(p).stream().filter(MediaScanner::isVideo).forEach(files::add);
                } catch (java.io.IOException ignored) {
                    // unreadable folder: skip it
                }
            } else if (MediaScanner.isVideo(p)) {
                files.add(p);
            }
        }
        return files;
    }

    /**
     * Attaches files to guide rows. A file whose name carries SxxEyy or a bare
     * episode number goes to that row; a date in the name goes to the episode
     * that aired on that day; anything left fills the remaining rows in order.
     */
    private void attachFiles(List<Path> files) {
        if (episodes.isEmpty()) {
            statusLabel.setText("pick a series and season first");
            return;
        }
        List<Path> fresh = files.stream().filter(f -> !attached.contains(f)).toList();
        int season = (Integer) seasonSpinner.getValue();
        Map<Integer, Path> map = autoAttach(fresh, episodes, season);
        for (Map.Entry<Integer, Path> entry : map.entrySet()) {
            setAttached(entry.getKey(), entry.getValue());
        }
        refreshGuideTable();
        statusLabel.setText(map.size() + " file" + (map.size() == 1 ? "" : "s") + " attached — "
                + attachedCount() + " of " + episodes.size() + " episodes have a file");
    }

    /**
     * Pure attach logic (package-visible for tests): maps guide-row index to
     * file. Priority: SxxEyy for this season, then a full air date in the name,
     * then a bare episode number, then order. The guide is the source of truth,
     * so we accept looser number hints here than the filename parser dares to.
     */
    static Map<Integer, Path> autoAttach(List<Path> files, List<EpisodeInfo> episodes, int season) {
        Map<Integer, Path> map = new java.util.LinkedHashMap<>();
        List<Path> leftovers = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            ReleaseInfo info = FileNameParser.parse(name, MediaScanner.extension(file));
            Integer epNumber = null;
            String airDate = null;
            if (info.hasEpisode() && (info.episode().season() == season || info.episode().season() == 0)) {
                epNumber = info.episode().episode();
            }
            if (epNumber == null) {
                Matcher m = DATE.matcher(name);
                if (m.find()) {
                    airDate = m.group(1) + "-" + m.group(2) + "-" + m.group(3);
                }
            }
            if (epNumber == null && airDate == null && info.hasAbsoluteEpisode()) {
                epNumber = info.absoluteEpisode();
            }
            if (epNumber == null && airDate == null) {
                epNumber = trailingNumber(name, info.year());
            }
            int row = -1;
            if (epNumber != null) {
                row = firstEmptyRowWithEpisode(episodes, map, epNumber);
            }
            if (row < 0 && airDate != null) {
                row = firstEmptyRowWithAirDate(episodes, map, airDate);
            }
            if (row >= 0) {
                map.put(row, file);
            } else {
                leftovers.add(file);
            }
        }
        leftovers.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        for (Path file : leftovers) {
            int row = -1;
            for (int i = 0; i < episodes.size(); i++) {
                if (!map.containsKey(i)) {
                    row = i;
                    break;
                }
            }
            if (row < 0) {
                break;
            }
            map.put(row, file);
        }
        return map;
    }

    /** A 1-3 digit number alone at the end of the name ("Die Drehscheibe 39.mkv"). */
    private static Integer trailingNumber(String name, int year) {
        String stem = name.replaceFirst("(?i)\\.[A-Za-z0-9]+$", "").replaceAll("[.\\-–—]+$", "");
        Matcher m = Pattern.compile("(?:^|\\s)(\\d{1,3})$").matcher(stem);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n > 0 && n != year) {
                return n;
            }
        }
        return null;
    }

    private static int firstEmptyRowWithEpisode(List<EpisodeInfo> episodes, Map<Integer, Path> map,
                                                int episodeNumber) {
        for (int i = 0; i < episodes.size(); i++) {
            if (!map.containsKey(i) && episodes.get(i).episode() == episodeNumber) {
                return i;
            }
        }
        return -1;
    }

    private static int firstEmptyRowWithAirDate(List<EpisodeInfo> episodes, Map<Integer, Path> map, String date) {
        for (int i = 0; i < episodes.size(); i++) {
            if (!map.containsKey(i) && date.equals(episodes.get(i).airDate())) {
                return i;
            }
        }
        return -1;
    }

    private void attachOne(int row) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Attach a file to " + episodes.get(row).name());
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        setAttached(row, chooser.getSelectedFile().toPath());
        refreshGuideTable();
        statusLabel.setText(attachedCount() + " of " + episodes.size() + " episodes have a file");
    }

    private void setAttached(int row, Path file) {
        if (file != null) {
            int previous = attached.indexOf(file);
            if (previous >= 0) {
                attached.set(previous, null); // one file, one episode
            }
        }
        attached.set(row, file);
    }

    private int attachedCount() {
        return (int) attached.stream().filter(java.util.Objects::nonNull).count();
    }

    private void refreshGuideTable() {
        while (attached.size() < episodes.size()) {
            attached.add(null);
        }
        while (attached.size() > episodes.size()) {
            attached.remove(attached.size() - 1);
        }
        episodesModel.setRowCount(0);
        for (int i = 0; i < episodes.size(); i++) {
            EpisodeInfo ep = episodes.get(i);
            Path file = attached.get(i);
            episodesModel.addRow(new Object[]{
                    ep.episode(),
                    String.format("S%02dE%02d", ep.season(), ep.episode()),
                    ep.name() == null ? "" : ep.name(),
                    ep.airDate() == null ? "" : ep.airDate(),
                    file == null ? "" : file.getFileName().toString()});
        }
    }

    // ---------------------------------------------------------------- rename

    /** Renames the attached files to "Show - S01E02 - Title.ext", in place. */
    private void renameAttached() {
        if (currentShow == null || attachedCount() == 0) {
            statusLabel.setText("attach some files first");
            return;
        }
        List<RenameOperation> plan = new ArrayList<>();
        for (int i = 0; i < episodes.size(); i++) {
            Path file = attached.get(i);
            if (file == null) {
                continue;
            }
            EpisodeInfo ep = episodes.get(i);
            StringBuilder base = new StringBuilder(currentShow.name())
                    .append(" - ").append(String.format("S%02dE%02d", ep.season(), ep.episode()));
            String title = ep.name();
            if (title != null && !title.isBlank() && !JUNK_TITLE.matcher(title).matches()) {
                base.append(" - ").append(title);
            }
            String ext = MediaScanner.extension(file);
            Path target = file.resolveSibling(sanitize(base) + (ext.isEmpty() ? "" : "." + ext));
            if (!target.equals(file)) {
                plan.add(new RenameOperation(file, target));
            }
        }
        if (plan.isEmpty()) {
            statusLabel.setText("attached files already have the right names");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Rename " + plan.size() + " file" + (plan.size() == 1 ? "" : "s")
                        + " to \"" + currentShow.name() + " - S01E02…\"?\nYou can undo this in History.",
                "Rename attached files", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        new Thread(() -> {
            try {
                RenameExecutor executor = new RenameExecutor(ConflictPolicy.INDEX, TransferAction.RENAME);
                List<RenameOperation> applied = new ArrayList<>();
                for (RenameExecutor.Result result : executor.apply(plan)) {
                    if (result.applied()) {
                        applied.add(new RenameOperation(result.operation().source(), result.actualTarget()));
                        int row = attached.indexOf(result.operation().source());
                        if (row >= 0) {
                            attached.set(row, result.actualTarget());
                        }
                    }
                }
                if (!applied.isEmpty()) {
                    new HistoryStore().record(applied);
                }
                SwingUtilities.invokeLater(() -> {
                    refreshGuideTable();
                    statusLabel.setText("renamed " + applied.size() + " file"
                            + (applied.size() == 1 ? "" : "s") + " — undo in History");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("rename failed: " + ex.getMessage()));
            }
        }).start();
    }

    /** Makes a name safe for the filesystem (the guide's titles come from the network). */
    private static String sanitize(StringBuilder base) {
        String s = base.toString().replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", " ")
                .replaceAll("\\s{2,}", " ").trim();
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    // ------------------------------------------------------- guide name tools

    private String namesJoined() {
        StringBuilder sb = new StringBuilder();
        for (EpisodeInfo ep : episodes) {
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
        statusLabel.setText(episodes.size() + " episode names copied to clipboard");
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
