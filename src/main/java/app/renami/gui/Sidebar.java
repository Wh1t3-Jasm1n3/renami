package app.renami.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ButtonGroup;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * The mode rail, in our design language: one icon-over-label cell per
 * view, big tap targets, and a subtle selection plate that keeps text and
 * icon fully legible in light and dark mode. Secondary views live behind a
 * "More" popup, and a header button collapses the rail to icons only.
 */
final class Sidebar extends JPanel {

    record Item(String id, String label, String icon, String color) {
    }

    private static final int WIDE = 150;
    private static final int NARROW = 72;

    private final JList<Item> list;
    private final List<Item> moreItems;
    private final Consumer<String> onSelect;
    private final java.util.function.BiConsumer<String, List<Path>> onFilesDropped;
    private final JLabel nameLabel;
    private final JButton collapseButton;
    private final JButton moreButton;
    private final JLabel moreLabel;
    private final JPanel footer;
    private boolean collapsed;

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

        DefaultListModel<Item> model = new DefaultListModel<>();
        items.forEach(model::addElement);
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setOpaque(false);
        list.setFocusable(false);
        list.setCellRenderer(new RailCell());
        list.setLayoutOrientation(JList.VERTICAL);
        list.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        list.addListSelectionListener(e -> {
            Item selected = list.getSelectedValue();
            if (selected != null) {
                onSelect.accept(selected.id());
            }
        });
        JScrollPane scroller = new JScrollPane(list);
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        add(scroller, BorderLayout.CENTER);

        // Cross-mode transfer: drop files onto a rail mode to switch and pipe them in
        list.setDropMode(javax.swing.DropMode.ON);
        list.setTransferHandler(new javax.swing.TransferHandler() {
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
                    List<java.io.File> files =
                            (List<java.io.File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    JList.DropLocation loc = list.getDropLocation();
                    int index = loc != null ? loc.getIndex() : -1;
                    Item item = index >= 0 && index < list.getModel().getSize()
                            ? list.getModel().getElementAt(index)
                            : list.getSelectedValue();
                    if (item == null) {
                        return false;
                    }
                    list.setSelectedValue(item, true);
                    onFilesDropped.accept(item.id(), files.stream().map(java.io.File::toPath).toList());
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });

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

        list.setSelectedIndex(0);
        if (System.getProperty("renami.collapsed") != null) {
            toggleCollapsed();
        }
    }

    /** Highlights the rail entry for the visible view (clears it for More views). */
    void setActive(String id) {
        boolean inMore = moreItems.stream().anyMatch(i -> i.id().equals(id));
        Item current = list.getSelectedValue();
        if (inMore) {
            if (current != null) {
                list.clearSelection();
            }
            return;
        }
        if (current != null && current.id().equals(id)) {
            return;
        }
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (list.getModel().getElementAt(i).id().equals(id)) {
                list.setSelectedIndex(i);
                return;
            }
        }
    }

    private void toggleCollapsed() {
        collapsed = !collapsed;
        setPreferredSize(new Dimension(collapsed ? NARROW : WIDE, 0));
        nameLabel.setVisible(!collapsed);
        moreLabel.setVisible(!collapsed);
        collapseButton.setIcon(Ui.icon(collapsed ? "expand" : "collapse", 15));
        collapseButton.setToolTipText(collapsed ? "Expand sidebar" : "Collapse sidebar");
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
        repaint();
    }

    /** Icon-over-label cell with a rounded selection plate. */
    private final class RailCell implements ListCellRenderer<Item> {
        @Override
        public Component getListCellRendererComponent(JList<? extends Item> list, Item item, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Cell cell = new Cell();
            JLabel icon = new JLabel(Ui.icon(item.icon(), 26, () -> Ui.hex(item.color())));
            icon.setAlignmentX(CENTER_ALIGNMENT);
            cell.add(Box.createVerticalStrut(9));
            cell.add(icon);
            if (!collapsed) {
                JLabel label = new JLabel(item.label());
                label.setAlignmentX(CENTER_ALIGNMENT);
                label.setFont(Ui.body().deriveFont(Font.PLAIN, 12f));
                label.setForeground(Ui.text());
                cell.add(Box.createVerticalStrut(5));
                cell.add(label);
            }
            cell.add(Box.createVerticalStrut(9));
            cell.selected = isSelected;
            cell.tint = Ui.hex(item.color());
            return cell;
        }
    }

    private static final class Cell extends JPanel {
        boolean selected;
        Color tint = Ui.accent();

        Cell() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (selected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // subtle plate, not a saturated fill: text and icon stay legible
                g2.setColor(Ui.dark() ? new Color(255, 255, 255, 26) : new Color(0, 0, 0, 22));
                g2.fillRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 10, 10);
                g2.setColor(tint);
                g2.fillRoundRect(4, 10, 3, getHeight() - 21, 3, 3);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
