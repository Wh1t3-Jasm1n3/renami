package app.renami.gui;

import app.renami.format.FormatEngine;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * List mode: generate a sequence of names from a format expression
 * with From/To counters, then copy the list or save it as a text file.
 */
public final class ListPanel extends JPanel {

    private final DefaultListModel<String> outputModel = new DefaultListModel<>();
    private final JList<String> output = new JList<>(outputModel);
    private final JTextField formatField = new JTextField("{n}", 24);
    private final SpinnerNumberModel fromModel = new SpinnerNumberModel(1, 0, 999_999, 1);
    private final SpinnerNumberModel toModel = new SpinnerNumberModel(10, 0, 999_999, 1);
    private final JSpinner fromSpinner = new JSpinner(fromModel);
    private final JSpinner toSpinner = new JSpinner(toModel);
    private final JButton sequenceButton = new JButton("Sequence");
    private final JButton copyButton = new JButton("Copy");
    private final JButton saveButton = new JButton("Save as…");
    private final JLabel statusLabel = new JLabel("enter a format and press Sequence");
    private final List<Path> files = new ArrayList<>();

    public ListPanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 10);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0;
        form.add(Ui.caption("Format"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(formatField, c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(Ui.caption("From"), c);
        c.gridx = 1; c.gridy = 1;
        form.add(fromSpinner, c);
        c.gridx = 2;
        form.add(Ui.caption("To"), c);
        c.gridx = 3;
        form.add(toSpinner, c);
        add(form, BorderLayout.NORTH);

        output.setFont(Ui.body());
        JScrollPane scroll = new JScrollPane(output);
        scroll.setBorder(BorderFactory.createTitledBorder("Generated names"));
        add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        sequenceButton.addActionListener(e -> sequence());
        copyButton.addActionListener(e -> copy());
        saveButton.addActionListener(e -> saveAs());
        buttons.add(Ui.prominent(sequenceButton));
        buttons.add(copyButton);
        buttons.add(saveButton);
        buttons.add(statusLabel);
        statusLabel.setForeground(Ui.secondary());
        add(buttons, BorderLayout.SOUTH);

        FileDrop.register(this, this::acceptFiles);
    }

    /** Rail transfer / drop target: the files seed the sequence variables. */
    public void acceptFiles(List<Path> dropped) {
        files.clear();
        files.addAll(dropped);
        statusLabel.setText(files.size() + " files — {fn} uses their names");
        sequence();
    }

    JButton runButton() {
        return sequenceButton;
    }

    /** Render the format once per counter value, using the From/To sequence. */
    private void sequence() {
        outputModel.clear();
        int from = fromModel.getNumber().intValue();
        int to = toModel.getNumber().intValue();
        if (to < from) {
            statusLabel.setText("To must be >= From");
            return;
        }
        String template = formatField.getText();
        for (int i = from; i <= to; i++) {
            Path file = files.size() > i - from ? files.get(i - from) : null;
            String base = file != null ? file.getFileName().toString() : String.valueOf(i);
            Map<String, Object> vars = FormatEngine.standardVars(base, null, null, null, null, null);
            vars.put("n", i);
            outputModel.addElement(FormatEngine.format(template, vars));
        }
        statusLabel.setText((to - from + 1) + " names generated");
    }

    private void copy() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < outputModel.size(); i++) {
            text.append(outputModel.get(i)).append('\n');
        }
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text.toString()), null);
        statusLabel.setText("list copied to clipboard");
    }

    /** Save as…: the native save panel writes the list to a text file. */
    private void saveAs() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        FileDialog chooser = new FileDialog(owner instanceof Frame f ? f : null, "Save list as", FileDialog.SAVE);
        chooser.setFile("renami-list.txt");
        chooser.setVisible(true);
        String file = chooser.getFile();
        if (file == null) {
            return;
        }
        Path target = java.nio.file.Paths.get(chooser.getDirectory(), file);
        try {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < outputModel.size(); i++) {
                lines.add(outputModel.get(i));
            }
            Files.write(target, lines, StandardCharsets.UTF_8);
            statusLabel.setText("saved " + target.getFileName());
        } catch (Exception e) {
            statusLabel.setText("could not save: " + e.getMessage());
        }
    }
}
