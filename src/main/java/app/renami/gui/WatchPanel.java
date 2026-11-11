package app.renami.gui;

import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.Datasources;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Watch view: pick a folder, press Start, and new media files are processed
 * the moment their download settles — the FileBot "-watch" feature with a
 * live activity log.
 */
public final class WatchPanel extends JPanel {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JTextField inputField = new JTextField(24);
    private final JSpinner settleSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 60, 1));
    private final SegmentedControl modeControl = new SegmentedControl("auto", "movie", "episode", "local");
    private final SegmentedControl actionControl =
            new SegmentedControl("rename", "move", "copy", "hardlink", "symlink");
    private final JComboBox<String> dbBox = new JComboBox<>(new String[]{"auto", "tmdb", "tvdb", "omdb", "kitsu", "local"});
    private final JTextField formatField = new JTextField("{n} - {s00e00} - {t}", 22);
    private final JCheckBox companionCheck = new JCheckBox("Companions", true);
    private final JCheckBox dryRunCheck = new JCheckBox("Dry run", false);

    private final JTextArea log = new JTextArea();
    private final JButton startButton = new JButton("Start");
    private final JLabel statusLabel = new JLabel(" ");

    private Path input;
    private WatchController controller;
    private Thread runner;

    public WatchPanel() {
        super(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        add(Ui.header("Watch"), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.add(Ui.card(buildOptions()), BorderLayout.NORTH);
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createEmptyBorder());
        log.setEditable(false);
        log.setFont(Ui.mono(12f));
        log.setLineWrap(true);
        body.add(Ui.card(logScroll), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(Ui.prominent(startButton));
        bar.add(buttons, BorderLayout.WEST);
        statusLabel.setForeground(Ui.secondary());
        bar.add(statusLabel, BorderLayout.CENTER);
        add(bar, BorderLayout.SOUTH);

        startButton.addActionListener(e -> toggle());
        FileDrop.register(this, dropped -> {
            if (dropped.size() == 1 && Files.isDirectory(dropped.get(0))) {
                input = dropped.get(0);
                inputField.setText(input.toString());
                statusLabel.setText("press Start to watch this folder");
            }
        });
    }

    /** The primary action, so the frame can make it the default (Return) button. */
    JButton startButton() {
        return startButton;
    }

    private JComponent buildOptions() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 18);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        JPanel folder = new JPanel(new BorderLayout(8, 0));
        folder.setOpaque(false);
        JButton browse = new JButton("Choose…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                input = chooser.getSelectedFile().toPath();
                inputField.setText(input.toString());
                statusLabel.setText("press Start to watch this folder");
            }
        });
        folder.add(inputField, BorderLayout.CENTER);
        folder.add(browse, BorderLayout.EAST);

        JPanel settle = new JPanel(new BorderLayout(6, 0));
        settle.setOpaque(false);
        settle.add(settleSpinner, BorderLayout.WEST);
        settle.add(Ui.caption("seconds settled"), BorderLayout.CENTER);

        c.gridx = 0; c.gridy = 0; c.weightx = 1;
        grid.add(field("Watch folder", folder, true), c);
        c.gridx = 1; c.gridy = 0; c.weightx = 0; c.insets = new Insets(4, 0, 4, 0);
        grid.add(field("Settle", settle, false), c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.insets = new Insets(4, 0, 4, 18);
        grid.add(field("Mode", modeControl, false), c);
        c.gridx = 1; c.gridy = 1; c.weightx = 1;
        grid.add(field("Action", actionControl, false), c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0;
        grid.add(field("Database", dbBox, true), c);
        c.gridx = 1; c.gridy = 2; c.weightx = 1;
        JPanel tail = new JPanel(new BorderLayout(0, 4));
        tail.setOpaque(false);
        tail.add(field("Format", formatField, true), BorderLayout.NORTH);
        JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        checks.setOpaque(false);
        checks.add(companionCheck);
        checks.add(dryRunCheck);
        tail.add(checks, BorderLayout.SOUTH);
        grid.add(tail, c);
        return grid;
    }

    private static JComponent field(String caption, JComponent control, boolean stretch) {
        JPanel field = new JPanel(new BorderLayout(0, 4));
        field.setOpaque(false);
        field.add(Ui.caption(caption), BorderLayout.NORTH);
        field.add(control, stretch ? BorderLayout.CENTER : BorderLayout.WEST);
        return field;
    }

    private void toggle() {
        if (runner != null) {
            stop();
            return;
        }
        if (input == null) {
            statusLabel.setText("pick a folder to watch first");
            return;
        }
        start();
    }

    private void start() {
        WatchController.Options options = new WatchController.Options(
                modeControl.getSelected(),
                formatField.getText().isBlank() ? null : formatField.getText(),
                actionControl.getSelected(),
                ConflictPolicy.INDEX,
                0.60,
                "default",
                companionCheck.isSelected(),
                !dryRunCheck.isSelected());
        controller = new WatchController(new HistoryStore());
        startButton.setText("Stop");
        statusLabel.setText("watching " + input);
        appendLog("watching " + input.toAbsolutePath());
        runner = new Thread(() -> {
            try {
                Datasource datasource = Datasources.create(Config.load(), (String) dbBox.getSelectedItem(), false);
                controller.run(input, (Integer) settleSpinner.getValue(), options, datasource, this::appendLog);
            } catch (Exception ex) {
                appendLog("error: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(this::stopped);
            }
        }, "renami-watch");
        runner.setDaemon(true);
        runner.start();
    }

    private void stop() {
        startButton.setEnabled(false);
        if (controller != null) {
            controller.stop();
        }
    }

    private void stopped() {
        runner = null;
        controller = null;
        startButton.setText("Start");
        startButton.setEnabled(true);
        statusLabel.setText("stopped");
        appendLog("stopped");
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            log.append("[" + CLOCK.format(LocalTime.now()) + "] " + message + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }
}
