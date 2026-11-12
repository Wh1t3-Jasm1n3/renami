package app.renami.gui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard: every icon/text button in the rename view must carry an action.
 * A factory that forgot addActionListener left History, Align and Exclude dead with no error.
 */
class ToolbarButtonsTest {

    @Test
    void everyButtonInRenameViewHasAnActionListener() throws Exception {
        List<JButton> buttons = new ArrayList<>();
        javax.swing.SwingUtilities.invokeAndWait(() -> collect(new RenamePanel(), buttons));
        assertFalse(buttons.isEmpty(), "rename view must contain buttons");
        boolean historySeen = false;
        for (JButton button : buttons) {
            // scrollbar arrows and similar chrome carry no text/tooltip and act via their model
            if (button.getText().isEmpty() && button.getToolTipText() == null) {
                continue;
            }
            assertTrue(button.getActionListeners().length > 0,
                    "button is dead (no action listener): tooltip=" + button.getToolTipText()
                            + " text=" + button.getText());
            historySeen |= "History".equals(button.getToolTipText());
        }
        assertTrue(historySeen, "the History toolbar button must exist");
    }

    private static void collect(Container container, List<JButton> out) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button) {
                out.add(button);
            } else if (child instanceof Container nested) {
                collect(nested, out);
            }
        }
    }
}
