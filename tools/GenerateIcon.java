// Generates packaging/AppIcon.iconset/*.png for the Renami macOS bundle.
// Run: java tools/GenerateIcon.java   (then: iconutil -c icns packaging/AppIcon.iconset)
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenerateIcon {

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

        // Full-bleed: fill the whole canvas with the rounded square (corner arc ~18.5%,
        // matching Apple's icon shape). A transparent margin makes macOS 26+ render a
        // system material plate *behind* the icon, which reads as a gray frame.
        float s = w;
        float off = 0f;
        float arc = s * 0.185f;
        Paint gradient = new GradientPaint(off, off, new Color(0x14, 0x6E, 0x64),
                off + s, off + s, new Color(0x0B, 0x35, 0x5E));
        g.setPaint(gradient);
        g.fill(new RoundRectangle2D.Float(off, off, s, s, arc, arc));

        // filmstrip motif along the left edge
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
