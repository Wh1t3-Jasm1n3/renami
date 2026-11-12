package app.renami.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionTest {

    @Test
    void comparesNumericSegments() {
        assertTrue(Version.parse("1.6.1").isNewerThan(Version.parse("1.6.0")));
        assertTrue(Version.parse("1.10.0").isNewerThan(Version.parse("1.9.9")));
        assertFalse(Version.parse("1.6.0").isNewerThan(Version.parse("1.6.1")));
        assertEquals(0, Version.parse("1.6").compareTo(Version.parse("1.6.0")));
    }

    @Test
    void toleratesReleaseTagNoise() {
        assertTrue(Version.parse("v1.7.0").isNewerThan(Version.parse("1.6.2")));
        assertTrue(Version.parse("1.7.0-beta1").isNewerThan(Version.parse("1.6.9")));
        assertTrue(Version.parse("0.0.0-dev").isNewerThan(Version.parse("0.0.0")) == false);
        assertTrue(Version.parse(null).compareTo(Version.parse("0.0.0")) == 0);
    }
}
