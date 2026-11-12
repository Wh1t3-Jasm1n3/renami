package app.renami.gui;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the build-time generated icon without looking at it: presence on the
 * classpath, correct size, and Apple's measured icon grid — the opaque shape at
 * ~80% of the canvas, centered, with transparent padding at the corners.
 * (Full-bleed artwork makes the Dock icon render ~24% larger than every other app.)
 */
class AppIconTest {

    @Test
    void iconIsGeneratedAndPackagedAsAResource() {
        BufferedImage image = AppIcon.load();
        assertNotNull(image, "app-icon.png missing — `gradle appIcon` must generate it");
        assertEquals(256, image.getWidth());
        assertEquals(256, image.getHeight());
    }

    @Test
    void iconSitsOnApplesMeasuredGrid() {
        BufferedImage image = AppIcon.load();
        assertNotNull(image);
        int w = image.getWidth();
        int h = image.getHeight();

        // canvas corners must be the transparent padding Apple's grid calls for
        for (int[] corner : new int[][]{{0, 0}, {w - 1, 0}, {0, h - 1}, {w - 1, h - 1}}) {
            int alpha = (image.getRGB(corner[0], corner[1]) >>> 24) & 0xFF;
            assertTrue(alpha < 16, "corner " + corner[0] + "," + corner[1]
                    + " must be transparent padding (alpha " + alpha + ")");
        }

        // the opaque bbox is ~80% of the canvas and centered
        int minX = w, maxX = -1, minY = h, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) > 8) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        double ratio = (maxX - minX + 1) / (double) w;
        assertTrue(ratio > 0.77 && ratio < 0.83,
                "shape must span ~80% of the canvas (Apple's grid), got " + ratio);
        assertTrue(Math.abs(minX - (w - 1 - maxX)) <= 1, "shape must be centered horizontally");
        assertTrue(Math.abs(minY - (h - 1 - maxY)) <= 1, "shape must be centered vertically");

        // the middle of each shape edge is opaque (the shape, not just its corners)
        int mid = w / 2;
        for (int[] edge : new int[][]{{mid, minY + 2}, {mid, maxY - 2}, {minX + 2, mid}, {maxX - 2, mid}}) {
            int alpha = (image.getRGB(edge[0], edge[1]) >>> 24) & 0xFF;
            assertEquals(0xFF, alpha, "shape edge " + edge[0] + "," + edge[1] + " must be opaque");
        }

        // the white "R" glyph sits in the middle: at least one bright pixel near the centre
        boolean bright = false;
        for (int y = 96; y < 160 && !bright; y++) {
            for (int x = 96; x < 192 && !bright; x++) {
                int argb = image.getRGB(x, y);
                bright = ((argb >>> 24) & 0xFF) == 0xFF
                        && (argb & 0xFFFFFF) > 0xC0C0C0;
            }
        }
        assertTrue(bright, "expected the white glyph in the icon centre");
    }
}
