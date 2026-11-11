package app.renami.gui;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.function.Consumer;

/**
 * macOS source-list sidebar: app header on top, one rounded accent selection
 * per view. Replaces the old tab strip.
 */
final class Sidebar extends JPanel {

    record Item(String id, String label, String icon) {
    }

    Sidebar(List<Item> items, Consumer<String> onSelect) {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Ui.surface());
        setPreferredSize(new Dimension(200, 0));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));
        JPanel title = new JPanel(new BorderLayout(8, 0));
        title.setOpaque(false);
        JLabel name = new JLabel("Renami");
        name.setFont(Ui.title(15f));
        name.setForeground(Ui.text());
        Icon app = AppIcon.load() == null ? null
                : new javax.swing.ImageIcon(AppIcon.load().getScaledInstance(20, 20, java.awt.Image.SCALE_SMOOTH));
        title.add(app == null ? new JLabel() : new JLabel(app), BorderLayout.WEST);
        title.add(name, BorderLayout.CENTER);
        header.add(title, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        DefaultListModel<Item> model = new DefaultListModel<>();
        items.forEach(model::addElement);
        JList<Item> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setOpaque(false);
        list.setFocusable(false);
        list.setCellRenderer(new SourceCell());
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

        list.setSelectedIndex(0);
    }

    /** Rounded accent pill behind the selected view. */
    private static final class SourceCell extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Item item = (Item) value;
            Cell label = new Cell();
            label.setText(item.label());
            label.setIcon(Ui.icon(item.icon(), 15));
            label.setFont(Ui.body().deriveFont(Font.PLAIN, 13f));
            label.setForeground(isSelected ? Color.WHITE : Ui.text());
            label.selected = isSelected;
            label.setOpaque(false);
            label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            return label;
        }
    }

    private static final class Cell extends javax.swing.JLabel {
        boolean selected;

        @Override
        protected void paintComponent(Graphics g) {
            if (selected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Ui.accent());
                g2.fillRoundRect(0, 1, getWidth() - 1, getHeight() - 3, 8, 8);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
