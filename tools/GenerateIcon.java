// Generates packaging/AppIcon.iconset/*.png for the Renami macOS bundle.
// Run: java -Djava.awt.headless=true tools/GenerateIcon.java
//      (then: iconutil -c icns packaging/AppIcon.iconset)
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenerateIcon {

    /**
     * Apple's icon grid, measured from /System/Applications AppIcon.icns on macOS 26:
     * the solid artwork bbox is 0.805 of the canvas, centered, with ~9.8% transparent
     * padding on every side. macOS normalizes each icon's opaque shape to the Dock and
     * Launchpad tile, so full-bleed artwork renders ~24% larger than every other icon.
     */
    private static final float SHAPE_RATIO = 0.80f;

    /** Apple's corner radius as a fraction of the shape (measured: ~0.224). */
    private static final float CORNER_FRACTION = 0.2237f;

    public static void main(String[] args) throws Exception {
        File dir = new File("packaging/AppIcon.iconset");
        dir.mkdirs();
        for (int base : new int[]{16, 32, 128, 256, 512}) {
            ImageIO.write(render(base, base), "png", new File(dir, "icon_" + base + "x" + base + ".png"));
            int hi = base * 2;
            ImageIO.write(render(hi, hi), "png", new File(dir, "icon_" + base + "x" + base + "@2x.png"));
        }
        System.out.println("wrote " + dir);
    }

    private static BufferedImage render(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // The shape sits at 80% of the canvas, centered — Apple's measured grid.
        // Its own corner radius matches the system mask so no gray material plate
        // shows between artwork and mask at any size.
        float s = w * SHAPE_RATIO;
        float off = (w - s) / 2f;
        float radius = s * CORNER_FRACTION;
        Paint gradient = new GradientPaint(off, off, new Color(0x14, 0x6E, 0x64),
                off + s, off + s, new Color(0x0B, 0x35, 0x5E));
        g.setPaint(gradient);
        g.fillRoundRect(Math.round(off), Math.round(off), Math.round(s), Math.round(s),
                Math.round(radius * 2), Math.round(radius * 2));

        // filmstrip motif along the left edge of the shape
        g.setColor(new Color(255, 255, 255, 46));
        int hole = Math.max(1, Math.round(s * 0.045f));
        int step = Math.max(hole * 2, Math.round(s / 6f));
        for (int y = Math.round(off + s * 0.12f); y < off + s * 0.85f; y += step) {
            g.fillRoundRect(Math.round(off + s * 0.07f), y, hole, hole, hole / 2, hole / 2);
        }

        // bold "R" glyph
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, Math.round(s * 0.56f)));
        FontMetrics fm = g.getFontMetrics();
        String glyph = "R";
        int tx = Math.round((w - fm.stringWidth(glyph)) / 2f + s * 0.06f);
        int ty = Math.round((w - fm.getHeight()) / 2f + fm.getAscent());
        g.drawString(glyph, tx, ty);

        // rename arrow under the glyph
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.02f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int ay = Math.round(off + s * 0.80f);
        int x1 = Math.round(off + s * 0.34f), x2 = Math.round(off + s * 0.66f);
        g.drawLine(x1, ay, x2, ay);
        g.drawLine(x2, ay, x2 - Math.round(s * 0.05f), ay - Math.round(s * 0.045f));
        g.drawLine(x2, ay, x2 - Math.round(s * 0.05f), ay + Math.round(s * 0.045f));
        g.dispose();
        return img;
    }
}
