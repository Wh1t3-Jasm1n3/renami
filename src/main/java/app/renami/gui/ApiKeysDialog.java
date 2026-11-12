package app.renami.gui;

import app.renami.util.Config;
import app.renami.web.ApiService;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * API-key panel: one row per metadata service, each with a
 * "Get key…" button that opens the provider's sign-up page and a "Test"
 * button that validates the stored key with a live lookup. Services that
 * need no key at all (TVmaze, Kitsu, Local) are listed too — that is the
 * point of the panel: a fresh install already resolves TV shows, and the
 * rows say so in plain terms.
 */
public final class ApiKeysDialog extends JDialog {

    /** One service row: its editable fields plus where to report test results. */
    private final class Row {
        final ApiService service;
        final JTextField keyField;
        final JTextField pinField;
        final JLabel status = new JLabel(" ");
        final JButton testButton;

        Row(ApiService service) {
            this.service = service;
            this.keyField = service.keyProperty() == null ? null : new JTextField(26);
            this.pinField = service.pinProperty() == null ? null : new JTextField(12);
            if (keyField != null) {
                keyField.setText(config.get(service.keyProperty()));
            }
            if (pinField != null) {
                pinField.setText(config.get(service.pinProperty()));
            }
            this.testButton = service.probeKind() == null ? null : new JButton("Test", Ui.icon("fetch", 12));
            if (testButton != null) {
                testButton.addActionListener(e -> test(this));
            }
            if (!service.needsKey()) {
                status.setText("no key needed — works out of the box");
                status.setForeground(Ui.secondary());
            }
        }
    }

    private final Config config = Config.load();
    private final List<Row> rows = new ArrayList<>();

    private ApiKeysDialog(Window owner) {
        super(owner, "API Keys", ModalityType.APPLICATION_MODAL);
        setResizable(false);
        AppIcon.applyTo(this);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));

        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        JLabel title = new JLabel("Metadata services");
        title.setFont(Ui.title(16f));
        title.setForeground(Ui.text());
        JLabel intro = new JLabel("<html><body style='width:460px'>Renami works with <b>no key at all</b>: "
                + "TV shows resolve through TVmaze and anime through Kitsu. Add one free TMDb key and "
                + "movies match too. Keys are stored only in ~/.renami/config.properties.</body></html>");
        intro.setForeground(Ui.secondary());
        head.add(title, BorderLayout.NORTH);
        head.add(intro, BorderLayout.SOUTH);
        body.add(head, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 0, 5, 10);
        c.anchor = GridBagConstraints.WEST;
        int y = 0;
        for (ApiService service : ApiService.all()) {
            Row row = new Row(service);
            rows.add(row);
            c.gridx = 0; c.gridy = y;
            grid.add(nameColumn(service), c);
            c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            grid.add(fieldColumn(row), c);
            c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            int buttons = (service.signupUrl() == null ? 0 : 1) + (row.testButton == null ? 0 : 1);
            JPanel actions = new JPanel(new GridLayout(Math.max(buttons, 1), 1, 0, 4));
            if (service.signupUrl() != null) {
                JButton get = new JButton("Get key…");
                get.addActionListener(e -> openUrl(service.signupUrl()));
                actions.add(get);
            }
            if (row.testButton != null) {
                actions.add(row.testButton);
            }
            grid.add(actions, c);
            y++;
        }
        body.add(grid, BorderLayout.CENTER);

        JButton save = Ui.prominent(new JButton("Save"));
        save.addActionListener(e -> save());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.add(close);
        footer.add(save);
        body.add(footer, BorderLayout.SOUTH);

        setContentPane(body);
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Service name plus its current terms, so users can judge what to pay for. */
    private static JComponent nameColumn(ApiService service) {
        JPanel column = new JPanel(new BorderLayout(0, 2));
        column.setOpaque(false);
        JLabel name = new JLabel(service.label());
        name.setFont(Ui.body().deriveFont(Font.BOLD, 13f));
        name.setForeground(Ui.text());
        JLabel terms = Ui.caption(service.terms());
        column.add(name, BorderLayout.NORTH);
        column.add(terms, BorderLayout.SOUTH);
        return column;
    }

    /** Key field (or the key-free note) with the per-row status line below it. */
    private JComponent fieldColumn(Row row) {
        JPanel column = new JPanel(new BorderLayout(0, 3));
        column.setOpaque(false);
        JPanel fields = new JPanel(new GridLayout(row.pinField == null ? 1 : 2, 1, 0, 4));
        fields.setOpaque(false);
        if (row.keyField != null) {
            fields.add(row.keyField);
        } else {
            JLabel free = new JLabel("—");
            free.setForeground(Ui.secondary());
            fields.add(free);
        }
        if (row.pinField != null) {
            row.pinField.putClientProperty("JTextField.placeholderText", "subscriber PIN (optional)");
            fields.add(row.pinField);
        }
        row.status.setFont(Ui.body().deriveFont(Font.PLAIN, 11f));
        column.add(fields, BorderLayout.NORTH);
        column.add(row.status, BorderLayout.SOUTH);
        return column;
    }

    private void save() {
        for (Row row : rows) {
            if (row.keyField != null) {
                String value = row.keyField.getText().trim();
                if (value.isEmpty()) {
                    Config.unset(row.service.keyProperty());
                } else {
                    Config.set(row.service.keyProperty(), value);
                }
            }
            if (row.pinField != null) {
                String value = row.pinField.getText().trim();
                if (value.isEmpty()) {
                    Config.unset(row.service.pinProperty());
                } else {
                    Config.set(row.service.pinProperty(), value);
                }
            }
        }
        for (Row row : rows) {
            if (row.service.needsKey()) {
                row.status.setText("saved");
                row.status.setForeground(Ui.secondary());
            }
        }
    }

    /** Live check against the provider, off the UI thread like every other lookup. */
    private void test(Row row) {
        row.testButton.setEnabled(false);
        row.status.setText("testing…");
        row.status.setForeground(Ui.secondary());
        new Thread(() -> {
            String message;
            boolean ok;
            try {
                message = ApiService.probe(row.service);
                ok = true;
            } catch (Exception e) {
                message = e.getMessage() == null ? e.toString() : e.getMessage();
                ok = false;
            }
            final String text = message;
            final Color color = ok ? new Color(0x30, 0xB0, 0x60) : new Color(0xE0, 0x4A, 0x4A);
            SwingUtilities.invokeLater(() -> {
                row.status.setText(text);
                row.status.setForeground(color);
                row.testButton.setEnabled(true);
            });
        }, "renami-api-test").start();
    }

    private static void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Could not open " + url + "\n" + e.getMessage(),
                    "Browser", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Opens the panel over the main window. */
    public static void show(Window owner) {
        new ApiKeysDialog(owner).setVisible(true);
    }

    /**
     * First-run guidance: with no key configured the app still works (TVmaze/Kitsu),
     * but movies will not match. Ask once which way the user wants to start, and
     * never ask again once they chose.
     */
    public static void setupIfUnconfigured(Frame owner) {
        Config config = Config.load();
        if ("true".equals(config.get("setup.done"))) {
            return;
        }
        boolean anyKey = false;
        for (ApiService service : ApiService.all()) {
            if (service.keyProperty() != null && config.get(service.keyProperty()) != null) {
                anyKey = true;
            }
        }
        if (anyKey) {
            Config.set("setup.done", "true");
            return;
        }
        String[] options = {"Add a free TMDb key…", "Continue without a key"};
        int choice = JOptionPane.showOptionDialog(owner,
                "<html><body style='width:340px'>Renami can already rename <b>TV shows</b> with no setup "
                        + "(TVmaze) and <b>anime</b> (Kitsu).<br><br>Movies need one free key: TMDb takes "
                        + "about two minutes and covers movies and TV.</body></html>",
                "Welcome to Renami", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[1]);
        Config.set("setup.done", "true");
        if (choice == 0) {
            show(owner);
        }
    }
}
