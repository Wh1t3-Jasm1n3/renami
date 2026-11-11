package app.renami.gui;

import com.formdev.flatlaf.FlatLaf;
import org.junit.jupiter.api.Test;

import javax.swing.UIManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiThemeTest {

    @Test
    void applyInstallsFlatLaf() {
        assertDoesNotThrow(GuiTheme::apply);
        assertTrue(UIManager.getLookAndFeel() instanceof FlatLaf, "FlatLaf must be active");
    }

    @Test
    void darkFlagMatchesInstalledLaf() {
        GuiTheme.apply();
        assertTrue(GuiTheme.isDark() == FlatLaf.isLafDark());
    }
}
