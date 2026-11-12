package app.renami.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ButtonGroup;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The mode rail, in our design language: one icon-over-label cell per
 * view, big tap targets, and a subtle selection plate that keeps text and
 * icon fully legible in light and dark mode. The cells share the rail's
 * full height evenly, so there is no dead zone under the icons that could
 * steal a click. Secondary views live behind a "More" popup, and a header
 * button collapses the rail to icons only.
 */
final class Sidebar extends JPanel {

    record Item(String id, String label, String icon, String color) {
    }

    private static final int WIDE = 150;
    private static final int NARROW = 72;

    private final JPanel rail = new JPanel(new GridLayout(0, 1, 0, 4));
    private final List<RailCell> cells = new ArrayList<>();
    private final List<Item> moreItems;
    private final Consumer<String> onSelect;
    private final java.util.function.BiConsumer<String, List<Path>> onFilesDropped;
    private final JLabel nameLabel;
    private final JButton collapseButton;
    private final JButton moreButton;
    private final JLabel moreLabel;
    private final JPanel footer;
    private boolean collapsed;
    private String selectedId;

    Sidebar(List<Item> items, List<Item> moreItems, Consumer<String> onSelect,
            java.util.function.BiConsumer<String, List<Path>> onFilesDropped) {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Ui.surface());
        setPreferredSize(new Dimension(WIDE, 0));
        this.moreItems = moreItems;
        this.onSelect = onSelect;
        this.onFilesDropped = onFilesDropped;

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(14, 12, 10, 8));
        JPanel title = new JPanel(new BorderLayout(8, 0));
        title.setOpaque(false);
        nameLabel = new JLabel("Renami");
        nameLabel.setFont(Ui.title(15f));
        nameLabel.setForeground(Ui.text());
        Icon app = AppIcon.load() == null ? null
                : new javax.swing.ImageIcon(AppIcon.load().getScaledInstance(20, 20, java.awt.Image.SCALE_SMOOTH));
        title.add(app == null ? new JLabel() : new JLabel(app), BorderLayout.WEST);
        title.add(nameLabel, BorderLayout.CENTER);
        header.add(title, BorderLayout.CENTER);
        collapseButton = new JButton(Ui.icon("collapse", 15));
        collapseButton.setBorderPainted(false);
        collapseButton.setContentAreaFilled(false);
        collapseButton.setFocusable(false);
        collapseButton.setToolTipText("Collapse sidebar");
        collapseButton.addActionListener(e -> toggleCollapsed());
        header.add(collapseButton, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Equal-height cells fill the whole rail: the icons spread over the
        // available height and clicking anywhere hits a real mode, never a
        // phantom last row like a JList would.
        rail.setOpaque(false);
        rail.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        for (Item item : items) {
            RailCell cell = new RailCell(item);
            cells.add(cell);
            rail.add(cell);
        }
        add(rail, BorderLayout.CENTER);

        footer = new JPanel(new BorderLayout(0, 4));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(6, 8, 10, 8));
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        moreButton = new JButton(Ui.icon("more", 18));
        moreButton.setFocusable(false);
        moreButton.setToolTipText("More views");
        moreButton.addActionListener(e -> showMorePopup(moreButton));
        moreLabel = new JLabel("More");
        moreLabel.setForeground(Ui.text());
        moreLabel.setFont(Ui.body().deriveFont(Font.PLAIN, 12f));
        row.add(moreButton);
        row.add(Box.createHorizontalStrut(6));
        row.add(moreLabel);
        row.add(Box.createHorizontalGlue());
        JButton themeButton = new JButton(Ui.icon("theme", 18));
        themeButton.setFocusable(false);
        themeButton.setToolTipText("Theme");
        themeButton.addActionListener(e -> showThemePopup(themeButton));
        row.add(themeButton);
        footer.add(row, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        if (!items.isEmpty()) {
            selectedId = items.get(0).id();
            onSelect.accept(items.get(0).id());
        }
        if (System.getProperty("renami.collapsed") != null) {
            toggleCollapsed();
        }
    }

    /** Highlights the rail entry for the visible view (clears it for More views). */
    void setActive(String id) {
        boolean inMore = moreItems.stream().anyMatch(i -> i.id().equals(id));
        String next = inMore ? null : id;
        if (java.util.Objects.equals(next, selectedId)) {
            return;
        }
        selectedId = next;
        rail.repaint();
    }

    private void selectFromUser(String id) {
        if (!id.equals(selectedId)) {
            selectedId = id;
            rail.repaint();
        }
        onSelect.accept(id);
    }

    private void toggleCollapsed() {
        collapsed = !collapsed;
        setPreferredSize(new Dimension(collapsed ? NARROW : WIDE, 0));
        nameLabel.setVisible(!collapsed);
        moreLabel.setVisible(!collapsed);
        collapseButton.setIcon(Ui.icon(collapsed ? "expand" : "collapse", 15));
        collapseButton.setToolTipText(collapsed ? "Expand sidebar" : "Collapse sidebar");
        cells.forEach(RailCell::buildContent);
        revalidate();
        repaint();
    }

    private void showMorePopup(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        for (Item item : moreItems) {
            JMenuItem mi = new JMenuItem(item.label(), Ui.icon(item.icon(), 16, () -> Ui.hex(item.color())));
            mi.addActionListener(e -> onSelect.accept(item.id()));
            menu.add(mi);
        }
        menu.show(anchor, anchor.getWidth() + 6, 0);
    }

    private void showThemePopup(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        String current = currentMode();
        ButtonGroup group = new ButtonGroup();
        for (String mode : List.of("auto", "light", "dark")) {
            javax.swing.JRadioButtonMenuItem mi = new javax.swing.JRadioButtonMenuItem(cap(mode));
            mi.setSelected(mode.equals(current));
            group.add(mi);
            mi.addActionListener(e -> {
                RenamiTheme.setTheme(mode);
                refresh();
                revalidate();
                repaint();
            });
            menu.add(mi);
        }
        menu.show(anchor, -menu.getPreferredSize().width - 6, anchor.getHeight());
    }

    private static String cap(String mode) {
        return switch (mode) {
            case "auto" -> "Auto (follow macOS)";
            case "light" -> "Light";
            default -> "Dark";
        };
    }

    private static String currentMode() {
        String mode = app.renami.util.Config.load().get("theme");
        return mode == null ? "auto" : mode;
    }

    /** Re-read the palette after a live theme switch. */
    void refresh() {
        setBackground(Ui.surface());
        nameLabel.setForeground(Ui.text());
        moreLabel.setForeground(Ui.text());
        moreButton.setIcon(Ui.icon("more", 16));
        collapseButton.setIcon(Ui.icon(collapsed ? "expand" : "collapse", 15));
        cells.forEach(RailCell::buildContent);
        repaint();
    }

    /** One mode cell: icon over label, rounded selection plate, file-drop target. */
    private final class RailCell extends JPanel {
        final Item item;

        RailCell(Item item) {
            this.item = item;
            // GridBag centers the icon/label stack inside the tall cell
            // (BorderLayout.CENTER would stretch it and BoxLayout stacks from the top).
            setLayout(new GridBagLayout());
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            buildContent();
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    selectFromUser(item.id());
                }
            });
            // Cross-mode transfer: drop files onto a rail mode to switch and pipe them in
            setTransferHandler(new javax.swing.TransferHandler() {
                @Override
                public boolean canImport(javax.swing.TransferHandler.TransferSupport support) {
                    return support.isDrop() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
                }

                @Override
                @SuppressWarnings("unchecked")
                public boolean importData(javax.swing.TransferHandler.TransferSupport support) {
                    if (!canImport(support)) {
                        return false;
                    }
                    try {
                        List<java.io.File> files = (List<java.io.File>) support.getTransferable()
                                .getTransferData(DataFlavor.javaFileListFlavor);
                        selectFromUser(item.id());
                        onFilesDropped.accept(item.id(), files.stream().map(java.io.File::toPath).toList());
                        return true;
                    } catch (Exception ex) {
                        return false;
                    }
                }
            });
        }

        /** Rebuilds the centered icon/label stack (also after collapse or theme change). */
        void buildContent() {
            removeAll();
            JPanel stack = new JPanel();
            stack.setOpaque(false);
            stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
            JLabel icon = new JLabel(Ui.icon(item.icon(), 26, () -> Ui.hex(item.color())));
            icon.setAlignmentX(CENTER_ALIGNMENT);
            stack.add(icon);
            if (!collapsed) {
                JLabel label = new JLabel(item.label());
                label.setAlignmentX(CENTER_ALIGNMENT);
                label.setFont(Ui.body().deriveFont(Font.PLAIN, 12f));
                label.setForeground(Ui.text());
                stack.add(Box.createVerticalStrut(5));
                stack.add(label);
            }
            // GridBagLayout centers this stack in the cell, both ways.
            add(stack);
            revalidate();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (item.id().equals(selectedId)) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // subtle plate, not a saturated fill: text and icon stay legible
                g2.setColor(Ui.dark() ? new Color(255, 255, 255, 26) : new Color(0, 0, 0, 22));
                g2.fillRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 10, 10);
                g2.setColor(Ui.hex(item.color()));
                g2.fillRoundRect(4, getHeight() / 2 - 14, 3, 28, 3, 3);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
