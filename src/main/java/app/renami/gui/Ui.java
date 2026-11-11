package app.renami.gui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;

/**
 * Shared visual vocabulary for the desktop UI: the theme palette, fonts,
 * line icons, rounded cards and the prominent (accent-filled) primary button.
 */
public final class Ui {

    private Ui() {
    }

    // ---- palette (resolved from the active FlatLaf theme) ----

    public static boolean dark() {
        return RenamiTheme.isDark();
    }

    public static Color text() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : Color.BLACK;
    }

    /** Muted colour for secondary labels and unselected segments. */
    public static Color secondary() {
        Color c = UIManager.getColor("TextField.inactiveForeground");
        return c != null ? c : fade(text(), 55);
    }

    /** Card surface: sits on top of the window background. */
    public static Color card() {
        Color c = UIManager.getColor("TextField.background");
        return c != null ? c : Color.WHITE;
    }

    /** Sidebar / track surface: a step away from the window background. */
    public static Color surface() {
        Color bg = UIManager.getColor("Panel.background");
        if (bg == null) {
            bg = dark() ? new Color(0x3C, 0x3F, 0x41) : new Color(0xF2, 0xF2, 0xF2);
        }
        return mix(bg, Color.BLACK, dark() ? 0.25 : 0.06);
    }

    public static Color accent() {
        Color c = UIManager.getColor("Button.default.background");
        return c != null ? c : (dark() ? new Color(0x0A, 0x84, 0xFF) : new Color(0x00, 0x71, 0xE3));
    }

    static Color mix(Color a, Color b, double t) {
        int r = (int) Math.round(a.getRed() * (1 - t) + b.getRed() * t);
        int g = (int) Math.round(a.getGreen() * (1 - t) + b.getGreen() * t);
        int bl = (int) Math.round(a.getBlue() * (1 - t) + b.getBlue() * t);
        return new Color(r, g, bl);
    }

    public static Color fade(Color c, double pctWhite) {
        double t = pctWhite / 100.0;
        int r = (int) Math.round(c.getRed() * (1 - t) + 255 * t);
        int g = (int) Math.round(c.getGreen() * (1 - t) + 255 * t);
        int b = (int) Math.round(c.getBlue() * (1 - t) + 255 * t);
        return new Color(r, g, b);
    }

    // ---- fonts ----

    public static Font body() {
        return UIManager.getFont("Label.font") != null ? UIManager.getFont("Label.font") : Font.getFont("SansSerif");
    }

    public static Font title(float size) {
        return body().deriveFont(Font.BOLD, size);
    }

    /** SF Mono from the system, for format templates and paths. */
    public static Font mono(float size) {
        try {
            File f = new File("/System/Library/Fonts/SFNSMono.ttf");
            if (f.isFile()) {
                return Font.createFont(Font.TRUETYPE_FONT, f).deriveFont(Font.PLAIN, size);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Font.getFont(Font.MONOSPACED).deriveFont(Font.PLAIN, size);
    }

    // ---- icons ----

    /** Tinted line icon from /icons, sized to the current text colour. */
    public static FlatSVGIcon icon(String name, int size) {
        FlatSVGIcon icon = new FlatSVGIcon("icons/" + name + ".svg", Ui.class.getClassLoader());
        FlatSVGIcon scaled = icon.derive((float) size / icon.getIconWidth());
        scaled.setColorFilter(new FlatSVGIcon.ColorFilter(c -> text()));
        return scaled;
    }

    // ---- components ----

    /** The primary action button: larger, and the frame's default (Return) button. */
    public static <T extends JButton> T prominent(T button) {
        button.putClientProperty("JComponent.sizeVariant", "large");
        return button;
    }

    /** Small muted caption above a control. */
    public static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(secondary());
        label.setFont(body().deriveFont(Font.PLAIN, 11f));
        return label;
    }

    /** View title, matching the Rename view's typography. */
    public static JLabel header(String title) {
        JLabel label = new JLabel(title);
        label.setFont(title(22f));
        label.setForeground(text());
        return label;
    }

    /** Flat, roomy table styling shared by every view. */
    public static void styleTable(javax.swing.JTable table) {
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setDefaultRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(javax.swing.JTable t, Object value,
                                                                    boolean sel, boolean focus, int row, int col) {
                java.awt.Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                ((javax.swing.JLabel) c).setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
                return c;
            }
        });
    }

    /** Rounded card panel with a hairline border. */
    public static JPanel card(JComponent contents) {
        JPanel card = new JPanel(new java.awt.BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(card());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(dark() ? fade(text(), 82) : new Color(0xE4, 0xE4, 0xEA));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 14, 12, 14));
        card.setLayout(new java.awt.BorderLayout(8, 8));
        card.add(contents, java.awt.BorderLayout.CENTER);
        return card;
    }

    /** Measures text width for sizing custom controls. */
    static int textWidth(Component c, String s) {
        FontMetrics fm = c.getFontMetrics(c.getFont());
        return fm.stringWidth(s);
    }

    static Dimension pad(Dimension d, int left, int top, int right, int bottom) {
        return new Dimension(d.width + left + right, d.height + top + bottom);
    }
}
