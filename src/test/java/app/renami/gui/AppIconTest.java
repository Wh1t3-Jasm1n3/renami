package app.renami.gui;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the build-time generated icon without looking at it: presence on the
 * classpath, correct size, and the expected rounded-square alpha geometry.
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
    void iconIsARoundedOpaqueSquareWithTransparentCorners() {
        BufferedImage image = AppIcon.load();
        assertNotNull(image);
        int center = image.getRGB(image.getWidth() / 2, image.getHeight() / 2);
        assertEquals(0xFF, (center >>> 24) & 0xFF, "center must be fully opaque");

        for (int[] corner : new int[][]{{0, 0}, {image.getWidth() - 1, 0},
                {0, image.getHeight() - 1}, {image.getWidth() - 1, image.getHeight() - 1}}) {
            int argb = image.getRGB(corner[0], corner[1]);
            assertEquals(0x00, (argb >>> 24) & 0xFF, "corner " + corner[0] + "," + corner[1] + " must be transparent");
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
