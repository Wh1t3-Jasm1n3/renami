package app.renami.gui;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * macOS-style segmented control: a rounded track with a sliding pill behind
 * the selected segment. Replaces the row of combo boxes the old UI used.
 */
public final class SegmentedControl extends JPanel {

    private final ButtonGroup group = new ButtonGroup();
    private final List<JToggleButton> segments = new ArrayList<>();
    private Consumer<String> onChange;

    public SegmentedControl(String... items) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(false);
        for (String item : items) {
            JToggleButton segment = new JToggleButton(item);
            segment.setContentAreaFilled(false);
            segment.setBorderPainted(false);
            segment.setFocusable(false);
            segment.setFont(Ui.body());
            segment.setForeground(Ui.secondary());
            segment.setMargin(new java.awt.Insets(4, 12, 4, 12));
            group.add(segment);
            segments.add(segment);
            add(segment);
            segment.addActionListener(e -> {
                repaint();
                if (onChange != null) {
                    onChange.accept(getSelected());
                }
            });
        }
        segments.get(0).setSelected(true);
        segments.get(0).setForeground(Ui.text());
        for (JToggleButton segment : segments) {
            segment.addChangeListener(e ->
                    segment.setForeground(segment.isSelected() ? Ui.text() : Ui.secondary()));
        }
    }

    public String getSelected() {
        for (JToggleButton segment : segments) {
            if (segment.isSelected()) {
                return segment.getText();
            }
        }
        return segments.get(0).getText();
    }

    public void onChange(Consumer<String> handler) {
        this.onChange = handler;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(Ui.dark() ? new Color(0x2B, 0x2E, 0x30) : new Color(0xE9, 0xE9, 0xEE));
        g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
        JToggleButton selected = segments.stream().filter(JToggleButton::isSelected).findFirst().orElse(null);
        if (selected != null) {
            g2.setColor(Ui.card());
            g2.fillRoundRect(selected.getX() + 2, selected.getY() + 2,
                    selected.getWidth() - 4, selected.getHeight() - 4, h - 4, h - 4);
        }
        g2.dispose();
    }
}
