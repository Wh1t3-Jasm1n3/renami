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
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Full-screen terminal UI: every Renami mode is a tab, mirroring the desktop
 * sidebar. Tab/Shift-Tab or Left/Right switch tabs, digits jump, Ctrl-C quits.
 */
public final class TuiApp {

    private final List<TuiView> views;
    private int current;

    public TuiApp() {
        this(List.of(new RenameView(), new RenameView("episode", "Episodes"),
                new SubtitlesView(), new ChecksumView(), new ListView(),
                new PostProcessView(), new WatchView(), new DuplicatesView(),
                new HistoryView(), new UpdateView()));
    }

    TuiApp(List<TuiView> views) {
        this.views = List.copyOf(views);
    }

    /** Runs the terminal loop until quit; returns the process exit code. */
    public int run() throws IOException {
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        // Default is CTRL_C_KILLS_APPLICATION -> System.exit(1) inside Lanterna before
        // we ever see the keystroke. TRAP delivers Ctrl-C as a normal KeyStroke so the
        // loop's isQuit check can exit cleanly with code 0.
        factory.setUnixTerminalCtrlCBehaviour(
                com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal.CtrlCBehaviour.TRAP);
        // Note: UnixTerminal always probes size with ESC[6n at startScreen (real
        // terminals answer it; scripted ptys must send e.g. ESC[24;80R back).
        return run(factory.createTerminal());
    }

    /** Testable core: runs the loop against any terminal (real or virtual). */
    int run(Terminal terminal) throws IOException {
        trace("run: terminal=" + terminal.getClass().getName());
        try (Screen screen = new TerminalScreen(terminal)) {
            screen.startScreen();
            trace("run: screen started, size=" + screen.getTerminalSize());
            views.get(current).onActivate();
            render(screen);
            // On a real terminal pollInput() blocks instead of returning null,
            // so read in a dedicated thread and drive the loop from a timed
            // queue poll: every 50 ms tick repaints, which keeps background
            // worker progress and final statuses on screen.
            BlockingQueue<KeyStroke> keys = new LinkedBlockingQueue<>();
            // Real terminals: pollInput() uses tryLock + non-blocking decode and drops
            // keystrokes, so read on a dedicated thread with blocking readInput(); the
            // main loop's 50 ms queue-poll tick keeps idle repaints alive.
            // Virtual terminals (tests): readInput() parks on the terminal monitor and
            // deadlocks screen.close(), so poll there instead.
            final boolean virtual = terminal instanceof DefaultVirtualTerminal;
            Thread reader = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        KeyStroke k = virtual ? terminal.pollInput() : terminal.readInput();
                        if (k == null) {
                            Thread.sleep(15);
                            continue;
                        }
                        if (k.getKeyType() == KeyType.EOF) {
                            keys.offer(new KeyStroke(KeyType.EOF));
                            return;
                        }
                        keys.put(k);
                    }
                } catch (Throwable closed) {
                    // terminal went away; end the main loop cleanly
                    trace("reader ended: " + closed);
                    keys.offer(new KeyStroke(KeyType.EOF));
                }
            }, "renami-tui-input");
            reader.setDaemon(true);
            reader.start();
            while (true) {
                KeyStroke key;
                try {
                    key = keys.poll(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    return 0;
                }
                if (key == null) {
                    screen.doResizeIfNecessary();
                    render(screen);
                    continue;
                }
                trace("key: " + key.getKeyType() + " ch=" + key.getCharacter()
                        + " ctrl=" + key.isCtrlDown());
                if (isQuit(key) || key.getKeyType() == KeyType.EOF) {
                    trace("quit via " + key.getKeyType() + " ctrl=" + key.isCtrlDown());
                    return 0;
                }
                if (screen.doResizeIfNecessary() != null) {
                    // repaint for the new size but still process this key
                    render(screen);
                }
                if (!views.get(current).onKey(key)) {
                    switch (key.getKeyType()) {
                        case Tab, ArrowRight -> select(current + 1);
                        case ReverseTab, ArrowLeft -> select(current - 1);
                        case Escape -> {
                            trace("quit via view-not-consuming Escape");
                            return 0;
                        }
                        default -> {
                            Character c = key.getCharacter();
                            if (c != null && c >= '1' && c <= '9' && c - '1' < views.size()) {
                                select(c - '1');
                            } else if (Character.valueOf('0').equals(c) && views.size() > 9) {
                                select(9);
                            }
                        }
                    }
                }
                render(screen);
            }
        }
    }

    /** Optional keystroke trace file (RENAMI_TUI_DEBUG=/path): Lanterna silences
     * System.err while the screen is active, so debugging goes to a file. */
    static void trace(String line) {
        String file = System.getenv("RENAMI_TUI_DEBUG");
        if (file == null) {
            return;
        }
        try (java.io.FileWriter w = new java.io.FileWriter(file, true)) {
            w.write(line + "\n");
        } catch (java.io.IOException ignored) {
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
        String brand = " Renami " + Version.get() + " ";
        g.setForegroundColor(TextColor.ANSI.WHITE);
        g.setBackgroundColor(TextColor.ANSI.BLUE);
        g.putString(0, 0, pad(brand, width));
        g.setBackgroundColor(TextColor.ANSI.DEFAULT);
        String[] labels = new String[views.size()];
        int total = 0;
        for (int i = 0; i < views.size(); i++) {
            labels[i] = " " + views.get(i).title() + " ";
            total += labels[i].length();
        }
        int avail = Math.max(0, width - brand.length());
        // when the tabs do not fit, show a window that always contains the active one
        int left = 0;
        int right = views.size();
        if (total > avail) {
            int used = labels[current].length();
            right = current + 1;
            while (right < labels.length && used + labels[right].length() <= avail) {
                used += labels[right++].length();
            }
            left = current - 1;
            while (left >= 0 && used + labels[left].length() <= avail) {
                used += labels[left--].length();
            }
            left++;
        }
        for (int i = left; i < right; i++) {
            if (i == current) {
                g.setForegroundColor(TextColor.ANSI.BLACK);
                g.setBackgroundColor(TextColor.ANSI.WHITE_BRIGHT);
            } else {
                g.setForegroundColor(TextColor.ANSI.WHITE);
                g.setBackgroundColor(TextColor.ANSI.BLACK_BRIGHT);
            }
            g.putString(brand.length() + offsetOf(labels, left, i), 0, labels[i]);
        }
        g.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    private static int offsetOf(String[] labels, int from, int index) {
        int x = 0;
        for (int i = from; i < index; i++) {
            x += labels[i].length();
        }
        return x;
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
}
