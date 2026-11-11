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

/** History panel: browse rename batches from the journal and undo any of them. */
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
        super(new BorderLayout(8, 8));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(batchTable), new JScrollPane(opTable));
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(refreshButton);
        bottom.add(undoButton);
        bottom.add(statusLabel);
        add(bottom, BorderLayout.SOUTH);

        refreshButton.addActionListener(e -> refresh());
        undoButton.addActionListener(e -> undoSelected());
        batchTable.getSelectionModel().addListSelectionListener(e -> showOpsAndEnableUndo());
        undoButton.setEnabled(false);

        refresh();
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
                    statusLabel.setText(report.restored() + " of " + report.total() + " files restored");
                    refresh();
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

    private static DefaultTableModel readOnlyModel(Object[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
}
