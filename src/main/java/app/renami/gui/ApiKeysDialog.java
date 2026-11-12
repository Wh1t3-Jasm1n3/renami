package app.renami.gui;

import app.renami.util.Config;
import app.renami.web.ApiService;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The one setup page: what already works with no key at all, then the three
 * providers you can add a key for — each row is one line: name, a masked key
 * field with an eye toggle, "Get key…" and "Test". Providers that need no key
 * (TVmaze, Kitsu, Local) are not offered as options; the green card says they
 * already work. Opened from Help ▸ Set Up Renami… and once on first run.
 */
public final class ApiKeysDialog extends JDialog {

    private static final char MASK = '\u2022';

    /** One provider row: masked field, eye toggle, and where test results appear. */
    private final class Row {
        final ApiService service;
        final JPasswordField keyField;
        final JTextArea status = new JTextArea(" ");
        final JButton testButton;

        Row(ApiService service) {
            this.service = service;
            this.keyField = new JPasswordField(20);
            keyField.setText(config.get(service.keyProperty()));
            this.testButton = new JButton("Test", Ui.icon("fetch", 12));
            testButton.addActionListener(e -> test(this));
            // A wrapping text area (not an HTML label) so long provider messages
            // wrap reliably inside their fixed slot instead of being clipped.
            status.setFont(Ui.body().deriveFont(Font.PLAIN, 11f));
            status.setLineWrap(true);
            status.setWrapStyleWord(true);
            status.setEditable(false);
            status.setFocusable(false);
            status.setOpaque(false);
            status.setBorder(null);
            status.setCursor(null);
        }

        String typedKey() {
            return new String(keyField.getPassword()).trim();
        }
    }

    private final Config config = Config.load();
    private final List<Row> rows = new ArrayList<>();
    private boolean firstRun;

    private ApiKeysDialog(Window owner) {
        super(owner, "Set Up Renami", ModalityType.APPLICATION_MODAL);
        setResizable(false);
        AppIcon.applyTo(this);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));

        JPanel head = new JPanel(new BorderLayout(0, 10));
        head.setOpaque(false);
        JLabel title = new JLabel("Set Up Renami");
        title.setFont(Ui.title(16f));
        title.setForeground(Ui.text());
        JLabel intro = new JLabel("<html><body style='width:460px'>Rename TV shows and anime "
                + "<b>right now — no setup</b>. One free TMDb key adds movies.</body></html>");
        intro.setForeground(Ui.secondary());
        head.add(title, BorderLayout.NORTH);
        JPanel headCenter = new JPanel(new BorderLayout(0, 10));
        headCenter.setOpaque(false);
        headCenter.add(intro, BorderLayout.NORTH);
        headCenter.add(keyFreeCard(), BorderLayout.SOUTH);
        head.add(headCenter, BorderLayout.CENTER);
        body.add(head, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 0, 6, 10);
        c.anchor = GridBagConstraints.NORTHWEST; // top-align: name, field and buttons share one line
        int y = 0;
        for (ApiService service : ApiService.all()) {
            if (!service.needsKey()) {
                continue; // key-free providers already work — the green card covers them
            }
            Row row = new Row(service);
            rows.add(row);
            c.gridx = 0; c.gridy = y;
            grid.add(nameColumn(service), c);
            c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            grid.add(fieldColumn(row), c);
            c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            JPanel actions = new JPanel(new GridLayout(1, 2, 6, 0));
            actions.setOpaque(false);
            JButton get = new JButton("Get key…");
            get.addActionListener(e -> openUrl(service.signupUrl()));
            actions.add(get);
            actions.add(row.testButton);
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

    /** The honest selling point: three providers answer with zero configuration. */
    private JComponent keyFreeCard() {
        JPanel rowsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        for (String[] feature : new String[][]{
                {"TV shows and episodes", "TVmaze"},
                {"Anime", "Kitsu"},
                {"Rename from the filename", "Local, offline"}}) {
            JLabel check = new JLabel(Ui.icon("check", 15, () -> new Color(0x30, 0xB0, 0x60)));
            rowsPanel.add(check, c);
            c.gridx++;
            JLabel text = new JLabel("<html><b>" + feature[0] + "</b>  <span style='color:gray'>"
                    + feature[1] + "</span></html>");
            text.setFont(Ui.body());
            c.weightx = 1;
            rowsPanel.add(text, c);
            c.gridx = 0;
            c.gridy++;
            c.weightx = 0;
            c.insets = new Insets(4, 0, 0, 0);
        }
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.fade(Ui.text(), 0.85)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        card.setBackground(Ui.card());
        JLabel label = new JLabel("Works right now — no key needed");
        label.setFont(Ui.title(13f));
        card.add(label, BorderLayout.NORTH);
        card.add(rowsPanel, BorderLayout.CENTER);
        return card;
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

    /** Masked key field with an eye toggle, the TMDb key-form hint, and the status line. */
    private JComponent fieldColumn(Row row) {
        JPanel column = new JPanel(new BorderLayout(0, 3));
        column.setOpaque(false);
        JPanel field = new JPanel(new BorderLayout(4, 0));
        field.setOpaque(false);
        row.keyField.setEchoChar(MASK);
        JToggleButton eye = new JToggleButton(Ui.icon("eye", 14));
        eye.setSelectedIcon(Ui.icon("eye-off", 14));
        eye.setBorderPainted(false);
        eye.setContentAreaFilled(false);
        eye.setFocusPainted(false);
        eye.setToolTipText("Show / hide the key");
        eye.addActionListener(e -> row.keyField.setEchoChar(eye.isSelected() ? (char) 0 : MASK));
        field.add(row.keyField, BorderLayout.CENTER);
        field.add(eye, BorderLayout.EAST);
        column.add(field, BorderLayout.NORTH);
        if ("tmdb".equals(row.service.id())) {
            JLabel hint = new JLabel("<html>Paste the v3 key or the long v4 token —<br>Renami detects which.</html>");
            hint.setFont(Ui.body().deriveFont(Font.PLAIN, 11f));
            hint.setForeground(Ui.secondary());
            column.add(hint, BorderLayout.CENTER);
        }
        row.status.setFont(Ui.body().deriveFont(Font.PLAIN, 11f));
        // Fixed slot (3 lines tall): a message appearing or wrapping never reflows the
        // rows or pushes the buttons sideways. The text area wraps at the column width.
        row.status.setPreferredSize(new Dimension(340, 54));
        column.add(row.status, BorderLayout.SOUTH);
        return column;
    }

    private void save() {
        for (Row row : rows) {
            saveRow(row);
        }
        for (Row row : rows) {
            setStatus(row, "Saved", Ui.secondary());
        }
    }

    /** Status text wraps inside the row's fixed slot — it never reflows the layout. */
    private static void setStatus(Row row, String text, Color color) {
        row.status.setText(text);
        row.status.setForeground(color);
    }

    private static void saveRow(Row row) {
        String value = row.typedKey();
        if (value.isEmpty()) {
            Config.unset(row.service.keyProperty());
        } else {
            Config.set(row.service.keyProperty(), value);
        }
    }

    /**
     * Live check with exactly what is in the field: the row is saved first so the
     * probe (which reads the config) tests the typed key, and the probe bypasses
     * the response cache — a cached answer would not prove the key works.
     */
    private void test(Row row) {
        saveRow(row);
        row.testButton.setEnabled(false);
        setStatus(row, "Testing…", Ui.secondary());
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
                setStatus(row, text, color);
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

    /** First-run sheet: closing it in any way marks setup as seen, so it never nags. */
    @Override
    public void dispose() {
        if (firstRun) {
            Config.set("setup.done", "true");
        }
        super.dispose();
    }

    /** Help ▸ Set Up Renami…: always available, so the page is never lost. */
    public static void show(Window owner) {
        new ApiKeysDialog(owner).setVisible(true);
    }

    /**
     * First run only: with no key configured the app still resolves TV and anime,
     * but movies will not match — show the page once, then never unasked again.
     */
    public static void showIfUnconfigured(Frame owner) {
        Config config = Config.load();
        if ("true".equals(config.get("setup.done"))) {
            return;
        }
        for (ApiService service : ApiService.all()) {
            if (service.keyProperty() != null && config.get(service.keyProperty()) != null) {
                Config.set("setup.done", "true");
                return;
            }
        }
        ApiKeysDialog dialog = new ApiKeysDialog(owner);
        dialog.firstRun = true;
        dialog.setVisible(true);
    }
}
