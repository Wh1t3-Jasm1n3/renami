package app.renami.gui;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the build-time generated icon without looking at it: presence on the
 * classpath, correct size, and the full-bleed square alpha geometry macOS expects.
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
    void iconIsAFullBleedOpaqueSquare() {
        // macOS 11+ (and macOS 26 in particular) masks every app icon into its own
        // rounded shape; artwork with transparent margins or rounded corners shows the
        // system material plate as a gray frame. The artwork must fill the canvas.
        BufferedImage image = AppIcon.load();
        assertNotNull(image);
        int center = image.getRGB(image.getWidth() / 2, image.getHeight() / 2);
        assertEquals(0xFF, (center >>> 24) & 0xFF, "center must be fully opaque");

        for (int[] point : new int[][]{{0, 0}, {image.getWidth() - 1, 0},
                {0, image.getHeight() - 1}, {image.getWidth() - 1, image.getHeight() - 1},
                {image.getWidth() / 2, 0}, {0, image.getHeight() / 2},
                {image.getWidth() / 2, image.getHeight() - 1}, {image.getWidth() - 1, image.getHeight() / 2}}) {
            int argb = image.getRGB(point[0], point[1]);
            assertEquals(0xFF, (argb >>> 24) & 0xFF, "pixel " + point[0] + "," + point[1] + " must be opaque (full-bleed artwork)");
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
