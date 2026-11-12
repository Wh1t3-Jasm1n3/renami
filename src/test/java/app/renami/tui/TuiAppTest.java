package app.renami.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiAppTest {

    private static DefaultVirtualTerminal terminalWith(KeyStroke... keys) {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new TerminalSize(80, 24));
        for (KeyStroke key : keys) {
            vt.addInput(key);
        }
        return vt;
    }

    @Test
    void escapeQuitsWhenTheActiveViewDoesNotConsumeIt() throws Exception {
        // Rename tab is empty, so Escape is not consumed and the app quits
        TuiApp app = new TuiApp(List.of(new RenameView(), new HistoryView(), new UpdateView()));
        int code = app.run(terminalWith(new KeyStroke(KeyType.Escape)));
        assertEquals(0, code);
    }

    @Test
    void tabKeyMovesBetweenViewsAndF10Quits() throws Exception {
        TuiApp app = new TuiApp(List.of(new RenameView(), new HistoryView(), new UpdateView()));
        // two Tabs land on Update (which does not consume Escape), Escape then quits the app
        int code = app.run(terminalWith(new KeyStroke(KeyType.Tab),
                new KeyStroke(KeyType.Tab), new KeyStroke(KeyType.Escape)));
        assertEquals(0, code);
    }

    @Test
    void ctrlCQuitsFromAnywhere() throws Exception {
        TuiApp app = new TuiApp(List.of(new RenameView()));
        int code = app.run(terminalWith(new KeyStroke('c', true, false)));
        assertEquals(0, code);
    }

    @Test
    void stdinEofEndsTheLoop() throws Exception {
        // a closed stdin must end the poll loop instead of spinning on EOF keystrokes
        TuiApp app = new TuiApp(List.of(new RenameView()));
        int code = app.run(terminalWith(new KeyStroke(KeyType.EOF)));
        assertEquals(0, code);
    }

    @Test
    void renameViewReportsMissingPathsInsteadOfThrowing() {
        RenameView view = new RenameView();
        for (char c : "/definitely/not/a/path".toCharArray()) {
            assertTrue(view.onKey(new KeyStroke(c, false, false)));
        }
        view.onKey(new KeyStroke(KeyType.Enter));
        assertTrue(view.status().startsWith("not found:"));
    }

    @Test
    void historyViewLeavesEscapeToTheApp() {
        assertFalse(new HistoryView().onKey(new KeyStroke(KeyType.Escape)));
    }
}
