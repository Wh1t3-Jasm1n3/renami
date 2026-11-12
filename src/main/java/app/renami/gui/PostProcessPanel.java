package app.renami.gui;

import app.renami.rename.ConflictPolicy;
import app.renami.rename.HistoryStore;
import app.renami.rename.RenameExecutor;
import app.renami.rename.RenameOperation;
import app.renami.util.Config;
import app.renami.web.Datasource;
import app.renami.web.Datasources;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Post-Process view: the one-shot flow — point at a folder (archives
 * included), pick formats, preview the plan, run it. Runs on the same
 * {@link PostProcessController} as the CLI.
 */
public final class PostProcessPanel extends JPanel {

    private final SegmentedControl modeControl = new SegmentedControl("auto", "movie", "episode", "local");
    private final SegmentedControl actionControl =
            new SegmentedControl("rename", "move", "copy", "hardlink", "symlink");
    private final JComboBox<String> dbBox = new JComboBox<>(new String[]{"auto", "tmdb", "omdb", "kitsu", "local"});
    private final JTextField tvFormatField = new JTextField("{n}/Season {s}/{n} - {s00e00} - {t}", 22);
    private final JTextField movieFormatField = new JTextField("{n} ({y})/{n} ({y})", 22);
    private final JTextField inputField = new JTextField(24);
    private final JTextField outputField = new JTextField(24);
    private final JCheckBox cleanCheck = new JCheckBox("Delete archives", false);
    private final JCheckBox companionCheck = new JCheckBox("Companions", true);

    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[]{"File", "New Name", "Status"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable table = new JTable(tableModel);

    private final JButton matchButton = new JButton("Match");
    private final JButton runButton = new JButton("Run");
    private final JLabel statusLabel = new JLabel(" ");

    private final PostProcessController controller = new PostProcessController(new HistoryStore());
    private Path input;
    private Path output;
    private PostProcessController.Preview preview;

    public PostProcessPanel() {
        super(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));


        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.add(Ui.card(buildOptions()), BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        Ui.styleTable(table);
        table.getColumnModel().getColumn(2).setCellRenderer(Ui.statusRenderer());
        body.add(tableScroll, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(matchButton);
        buttons.add(Ui.prominent(runButton));
        bar.add(buttons, BorderLayout.WEST);
        statusLabel.setForeground(Ui.secondary());
        bar.add(statusLabel, BorderLayout.CENTER);
        add(bar, BorderLayout.SOUTH);

        matchButton.addActionListener(e -> match());
        runButton.addActionListener(e -> run());
        runButton.setEnabled(false);
        FileDrop.register(this, dropped -> {
            if (dropped.size() == 1 && Files.isDirectory(dropped.get(0))) {
                setInput(dropped.get(0));
            } else {
                statusLabel.setText("drop a folder — archives are extracted automatically");
            }
        });
    }

    /** The primary action, so the frame can make it the default (Return) button. */
    JButton runButton() {
        return runButton;
    }

    /** Preload a folder opened from the command line. */
    public void preload(List<Path> dropped) {
        if (dropped.size() == 1 && Files.isDirectory(dropped.get(0))) {
            setInput(dropped.get(0));
        }
    }

    private void setInput(Path folder) {
        input = folder;
        inputField.setText(folder.toString());
        statusLabel.setText("press Match to extract archives and look them up");
    }

    private JComponent buildOptions() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 18);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        c.gridx = 0; c.gridy = 0;
        grid.add(field("Input folder", folderPicker(true), true), c);
        c.gridx = 1; c.gridy = 0; c.weightx = 1;
        grid.add(field("Output folder (optional)", folderPicker(false), true), c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        grid.add(field("Mode", modeControl, false), c);
        c.gridx = 1; c.gridy = 1; c.weightx = 1;
        grid.add(field("Action", actionControl, false), c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0;
        grid.add(field("Database", dbBox, true), c);
        c.gridx = 1; c.gridy = 2; c.weightx = 1;
        grid.add(field("Episode format", tvFormatField, true), c);

        c.gridx = 0; c.gridy = 3; c.weightx = 0;
        grid.add(field("Movie format", movieFormatField, true), c);
        c.gridx = 1; c.gridy = 3; c.weightx = 0; c.insets = new Insets(4, 0, 4, 0);
        JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        checks.setOpaque(false);
        checks.add(cleanCheck);
        checks.add(companionCheck);
        JPanel checksField = new JPanel(new BorderLayout(0, 4));
        checksField.setOpaque(false);
        checksField.add(Ui.caption("After run"), BorderLayout.NORTH);
        checksField.add(checks, BorderLayout.CENTER);
        grid.add(checksField, c);
        return grid;
    }

    private JComponent folderPicker(boolean inputPicker) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JTextField field = inputPicker ? inputField : outputField;
        JButton browse = new JButton("Choose…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                Path folder = chooser.getSelectedFile().toPath();
                field.setText(folder.toString());
                if (inputPicker) {
                    setInput(folder);
                } else {
                    output = folder;
                }
            }
        });
        row.add(field, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);
        return row;
    }

    private static JComponent field(String caption, JComponent control, boolean stretch) {
        JPanel field = new JPanel(new BorderLayout(0, 4));
        field.setOpaque(false);
        field.add(Ui.caption(caption), BorderLayout.NORTH);
        field.add(control, stretch ? BorderLayout.CENTER : BorderLayout.WEST);
        return field;
    }

    private PostProcessController.Options options() {
        return new PostProcessController.Options(
                modeControl.getSelected(),
                tvFormatField.getText(),
                movieFormatField.getText(),
                actionControl.getSelected(),
                ConflictPolicy.INDEX,
                0.60,
                "default",
                cleanCheck.isSelected(),
                companionCheck.isSelected());
    }

    private void match() {
        if (input == null) {
            statusLabel.setText("pick an input folder first");
            return;
        }
        matchButton.setEnabled(false);
        runButton.setEnabled(false);
        statusLabel.setText("extracting and matching…");
        new SwingWorker<PostProcessController.Preview, Void>() {
            @Override
            protected PostProcessController.Preview doInBackground() throws Exception {
                Datasource datasource = Datasources.create(Config.load(), (String) dbBox.getSelectedItem(), false);
                return controller.plan(input, output, options(), datasource, msg -> statusLabel.setText(msg));
            }

            @Override
            protected void done() {
                try {
                    preview = get();
                    showPlan(preview.plan());
                    statusLabel.setText(preview.plan().size() + " operations planned"
                            + (preview.archives().isEmpty() ? "" : " (" + preview.archives().size() + " archives extracted)"));
                    runButton.setEnabled(!preview.plan().isEmpty());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("match failed");
                    JOptionPane.showMessageDialog(PostProcessPanel.this, cause.getMessage(),
                            "Match failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    matchButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void showPlan(List<RenameOperation> plan) {
        tableModel.setRowCount(0);
        for (RenameOperation op : plan) {
            tableModel.addRow(new Object[]{
                    op.source().getFileName(),
                    relativeTarget(op),
                    "planned"});
        }
    }

    private String relativeTarget(RenameOperation op) {
        Path base = output != null ? output : op.source().getParent();
        try {
            return base.relativize(op.target()).toString();
        } catch (IllegalArgumentException differentRoots) {
            return op.target().toString();
        }
    }

    private void run() {
        if (preview == null || preview.plan().isEmpty()) {
            return;
        }
        int confirmed = JOptionPane.showConfirmDialog(this,
                "Execute " + preview.plan().size() + " operations?\n"
                        + "Archives stay unless \"Delete archives\" is on and everything processed.",
                "Confirm post-process", JOptionPane.YES_NO_OPTION);
        if (confirmed != JOptionPane.YES_OPTION) {
            return;
        }
        runButton.setEnabled(false);
        statusLabel.setText("running…");
        new SwingWorker<List<RenameExecutor.Result>, Void>() {
            @Override
            protected List<RenameExecutor.Result> doInBackground() throws Exception {
                return controller.apply(preview, options(), null, msg -> statusLabel.setText(msg));
            }

            @Override
            protected void done() {
                try {
                    List<RenameExecutor.Result> results = get();
                    tableModel.setRowCount(0);
                    for (RenameExecutor.Result r : results) {
                        tableModel.addRow(new Object[]{
                                r.operation().source().getFileName(),
                                r.actualTarget().getFileName(),
                                r.status()});
                    }
                    long applied = results.stream().filter(RenameExecutor.Result::applied).count();
                    statusLabel.setText(applied + " operations applied. Use `renami undo` to revert.");
                    preview = null;
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("run failed");
                    JOptionPane.showMessageDialog(PostProcessPanel.this, cause.getMessage(),
                            "Post-process failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    runButton.setEnabled(false);
                }
            }
        }.execute();
    }
}
