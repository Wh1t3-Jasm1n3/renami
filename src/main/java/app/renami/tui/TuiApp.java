package app.renami.tui;

import app.renami.util.Version;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.util.List;

/**
 * Full-screen terminal UI: every Renami mode is a tab, mirroring the desktop
 * sidebar. Tab/Shift-Tab or Left/Right switch tabs, digits jump, Ctrl-C quits.
 */
public final class TuiApp {

    private final List<TuiView> views;
    private int current;

    public TuiApp() {
        this(List.of(new RenameView(), new HistoryView(), new UpdateView(),
                new PlaceholderView("Episodes", "rename --mode episode"),
                new PlaceholderView("Post-Process", "post-process"),
                new PlaceholderView("Watch", "watch"),
                new PlaceholderView("Subtitles", "subtitles"),
                new PlaceholderView("Duplicates", "duplicates"),
                new PlaceholderView("Checksum", "checksum"),
                new PlaceholderView("List", "list")));
    }

    TuiApp(List<TuiView> views) {
        this.views = List.copyOf(views);
    }

    /** Runs the terminal loop until quit; returns the process exit code. */
    public int run() throws IOException {
        return run(new DefaultTerminalFactory().createTerminal());
    }

    /** Testable core: runs the loop against any terminal (real or virtual). */
    int run(Terminal terminal) throws IOException {
        try (Screen screen = new TerminalScreen(terminal)) {
            screen.startScreen();
            views.get(current).onActivate();
            render(screen);
            while (true) {
                KeyStroke key = screen.readInput();
                if (isQuit(key)) {
                    return 0;
                }
                if (screen.doResizeIfNecessary() != null) {
                    render(screen);
                    continue;
                }
                if (!views.get(current).onKey(key)) {
                    switch (key.getKeyType()) {
                        case Tab, ArrowRight -> select(current + 1);
                        case ReverseTab, ArrowLeft -> select(current - 1);
                        case Escape -> {
                            return 0;
                        }
                        default -> {
                            Character c = key.getCharacter();
                            if (c != null && c >= '1' && c <= '9' && c - '1' < views.size()) {
                                select(c - '1');
                            }
                        }
                    }
                }
                render(screen);
            }
        }
    }

    /** Ctrl-C (delivered as a ctrl-down 'c' keystroke) or F10 quits. */
    private static boolean isQuit(KeyStroke key) {
        return key.getKeyType() == KeyType.F10
                || (key.isCtrlDown() && Character.valueOf('c').equals(key.getCharacter()));
    }

    private void select(int index) {
        if (index >= 0 && index < views.size() && index != current) {
            current = index;
            views.get(current).onActivate();
        }
    }

    private void render(Screen screen) throws IOException {
        TerminalSize size = screen.getTerminalSize();
        int width = size.getColumns();
        int height = size.getRows();
        TextGraphics g = screen.newTextGraphics();
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.setBackgroundColor(TextColor.ANSI.DEFAULT);
        g.fill(' '); // wipe the previous frame before redrawing
        drawTabBar(g, width);
        views.get(current).render(g, width, height);
        drawStatus(g, width, height, views.get(current).status());
        screen.refresh();
    }

    private void drawTabBar(TextGraphics g, int width) {
        g.setForegroundColor(TextColor.ANSI.WHITE);
        g.setBackgroundColor(TextColor.ANSI.BLUE);
        g.putString(0, 0, pad(" Renami " + Version.get() + " ", width));
        g.setBackgroundColor(TextColor.ANSI.DEFAULT);
        int x = 1;
        for (int i = 0; i < views.size(); i++) {
            String label = " " + views.get(i).title() + " ";
            if (i == current) {
                g.setForegroundColor(TextColor.ANSI.BLACK);
                g.setBackgroundColor(TextColor.ANSI.WHITE_BRIGHT);
            } else {
                g.setForegroundColor(TextColor.ANSI.WHITE);
                g.setBackgroundColor(TextColor.ANSI.BLACK_BRIGHT);
            }
            g.putString(x, 0, label);
            x += label.length();
        }
        g.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    private static void drawStatus(TextGraphics g, int width, int height, String status) {
        g.setForegroundColor(TextColor.ANSI.WHITE);
        g.setBackgroundColor(TextColor.ANSI.BLUE);
        g.putString(0, height - 1, pad(" " + status, width));
        g.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s.substring(0, width) : s + " ".repeat(width - s.length());
    }

    /** Tab content that defers to the CLI until its TUI implementation lands. */
    static final class PlaceholderView implements TuiView {
        private final String title;
        private final String command;

        PlaceholderView(String title, String command) {
            this.title = title;
            this.command = command;
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public void render(TextGraphics g, int width, int height) {
            g.putString(2, 2, title + " — coming to the TUI in 1.7.x.");
            g.putString(2, 3, "For now: the desktop app, or `renami " + command + "`.");
        }

        @Override
        public boolean onKey(KeyStroke key) {
            return false;
        }

        @Override
        public String status() {
            return "Tab/←→ switch · 1-9 jump · Ctrl-C quit";
        }
    }
}
