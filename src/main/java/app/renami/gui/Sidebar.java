package app.renami.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
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
 * FileBot's mode rail, in our design language: one icon-over-label cell per
 * view, big tap targets, and a subtle selection plate that keeps text and
 * icon fully legible in light and dark mode.
 */
final class Sidebar extends JPanel {

    record Item(String id, String label, String icon) {
    }

    Sidebar(List<Item> items, Consumer<String> onSelect) {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Ui.surface());
        setPreferredSize(new Dimension(150, 0));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(14, 12, 10, 12));
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

        list.setSelectedIndex(0);
    }

    /** Icon-over-label cell with a rounded selection plate (FileBot's rail). */
    private static final class RailCell implements ListCellRenderer<Item> {
        @Override
        public Component getListCellRendererComponent(JList<? extends Item> list, Item item, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Cell cell = new Cell();
            JLabel icon = new JLabel(Ui.icon(item.icon(), 22));
            icon.setAlignmentX(CENTER_ALIGNMENT);
            JLabel label = new JLabel(item.label());
            label.setAlignmentX(CENTER_ALIGNMENT);
            label.setFont(Ui.body().deriveFont(Font.PLAIN, 12f));
            label.setForeground(Ui.text());
            cell.add(Box.createVerticalStrut(8));
            cell.add(icon);
            cell.add(Box.createVerticalStrut(4));
            cell.add(label);
            cell.add(Box.createVerticalStrut(8));
            cell.selected = isSelected;
            return cell;
        }
    }

    private static final class Cell extends JPanel {
        boolean selected;

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
                g2.setColor(Ui.accent());
                g2.fillRoundRect(4, 10, 3, getHeight() - 21, 3, 3);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
