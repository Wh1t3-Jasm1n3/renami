package app.renami.gui;

import app.renami.rename.HistoryStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** History view: browse rename batches from the journal and undo any of them. */
public final class HistoryPanel extends JPanel {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final DefaultTableModel batchModel = readOnlyModel(new Object[]{"Time", "Files", "Actions", "Status"});
    private final JTable batchTable = new JTable(batchModel);
    private final DefaultTableModel opModel = readOnlyModel(new Object[]{"From", "To", "Action"});
    private final JTable opTable = new JTable(opModel);
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton undoButton = new JButton("Undo batch");
    private final JLabel statusLabel = new JLabel(" ");

    private List<HistoryStore.Batch> batches = List.of();
    private final HistoryController controller = new HistoryController(new HistoryStore());

    public HistoryPanel() {
        super(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));


        JScrollPane batchScroll = new JScrollPane(batchTable);
        batchScroll.setBorder(BorderFactory.createEmptyBorder());
        Ui.styleTable(batchTable);
        batchTable.getColumnModel().getColumn(3).setCellRenderer(new BatchStatusRenderer());
        JScrollPane opScroll = new JScrollPane(opTable);
        opScroll.setBorder(BorderFactory.createEmptyBorder());
        Ui.styleTable(opTable);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, batchScroll, opScroll);
        split.setResizeWeight(0.6);
        split.setDividerSize(8);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(refreshButton);
        buttons.add(Ui.prominent(undoButton));
        bar.add(buttons, BorderLayout.WEST);
        statusLabel.setForeground(Ui.secondary());
        bar.add(statusLabel, BorderLayout.CENTER);
        add(bar, BorderLayout.SOUTH);

        refreshButton.addActionListener(e -> refresh());
        undoButton.addActionListener(e -> undoSelected());
        batchTable.getSelectionModel().addListSelectionListener(e -> showOpsAndEnableUndo());
        undoButton.setEnabled(false);

        refresh();
    }

    /** The primary action, so the frame can make it the default (Return) button. */
    JButton undoButton() {
        return undoButton;
    }

    private void refresh() {
        try {
            batches = controller.batches();
        } catch (Exception ex) {
            statusLabel.setText("cannot read history: " + ex.getMessage());
            return;
        }
        batchModel.setRowCount(0);
        for (HistoryStore.Batch batch : batches) {
            batchModel.addRow(new Object[]{
                    TIME.format(Instant.ofEpochMilli(batch.time())),
                    batch.ops().size(),
                    summarizeVerbs(batch),
                    batch.undone() ? "undone" : "active"});
        }
        opModel.setRowCount(0);
        undoButton.setEnabled(false);
        statusLabel.setText(batches.isEmpty() ? "no recorded batches yet" : batches.size() + " batches");
    }

    private void showOpsAndEnableUndo() {
        opModel.setRowCount(0);
        int row = batchTable.getSelectedRow();
        if (row < 0 || row >= batches.size()) {
            undoButton.setEnabled(false);
            return;
        }
        HistoryStore.Batch batch = batches.get(row);
        for (String[] op : batch.ops()) {
            opModel.addRow(new Object[]{op[0], op[1], op.length > 2 ? op[2] : "renamed"});
        }
        undoButton.setEnabled(!batch.undone());
    }

    private void undoSelected() {
        int row = batchTable.getSelectedRow();
        if (row < 0 || row >= batches.size()) {
            return;
        }
        HistoryStore.Batch batch = batches.get(row);
        int confirmed = JOptionPane.showConfirmDialog(this,
                "Undo the batch from " + TIME.format(Instant.ofEpochMilli(batch.time())) + " ("
                        + batch.ops().size() + " files)?\n"
                        + "Renamed/moved files are restored; created copies and links are deleted.",
                "Confirm undo", JOptionPane.YES_NO_OPTION);
        if (confirmed != JOptionPane.YES_OPTION) {
            return;
        }
        refreshButton.setEnabled(false);
        undoButton.setEnabled(false);
        statusLabel.setText("undoing…");
        new SwingWorker<HistoryController.UndoReport, Void>() {
            @Override
            protected HistoryController.UndoReport doInBackground() throws Exception {
                return controller.undo(batch.id());
            }

            @Override
            protected void done() {
                try {
                    HistoryController.UndoReport report = get();
                    // refresh() resets the status line, so report the outcome after it
                    refresh();
                    statusLabel.setText(report.restored() + " of " + report.total() + " files restored");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("undo failed");
                    JOptionPane.showMessageDialog(HistoryPanel.this, cause.getMessage(),
                            "Undo failed", JOptionPane.ERROR_MESSAGE);
                    refreshButton.setEnabled(true);
                    showOpsAndEnableUndo();
                }
            }
        }.execute();
    }

    private static String summarizeVerbs(HistoryStore.Batch batch) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String[] op : batch.ops()) {
            counts.merge(op.length > 2 ? op[2] : "renamed", 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    /** Status column: green for active batches, muted for undone ones. */
    private static final class BatchStatusRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                c.setForeground("active".equals(value)
                        ? new Color(0x30, 0xB0, 0x60)
                        : Ui.secondary());
            }
            return c;
        }
    }

    private static DefaultTableModel readOnlyModel(Object[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
}
