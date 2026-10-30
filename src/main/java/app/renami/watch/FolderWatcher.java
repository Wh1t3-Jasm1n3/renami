package app.renami.watch;

import app.renami.media.MediaScanner;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Watches a folder tree for new media files and hands them to a callback once they have
 * "settled" — i.e. their size stopped changing, which is how a finished download looks.
 * Mirrors the folder-watch service of the classic CLI ({@code -watch}).
 */
public final class FolderWatcher implements AutoCloseable {

    private final Path root;
    private final long settleMillis;
    private final WatchService watchService = FileSystems.getDefault().newWatchService();
    private final Map<WatchKey, Path> keys = new HashMap<>();
    /** path → {last observed size, millis at which the size last changed} */
    private final Map<Path, long[]> pending = new HashMap<>();
    private final Set<Path> handedOver = new HashSet<>();
    private volatile boolean running = true;

    public FolderWatcher(Path root, Duration settle) throws IOException {
        this.root = root.toAbsolutePath();
        this.settleMillis = Math.max(100, settle.toMillis());
        registerTree(this.root);
    }

    /** Blocks until {@link #stop()}, feeding batches of settled media files to {@code callback}. */
    public void awaitBatches(Consumer<List<Path>> callback) throws InterruptedException {
        while (running) {
            WatchKey key = watchService.poll(500, TimeUnit.MILLISECONDS);
            if (key != null) {
                Path dir = keys.get(key);
                boolean overflow = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        overflow = true;
                        continue;
                    }
                    if (dir == null) {
                        continue;
                    }
                    Path child = dir.resolve(event.context().toString());
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        if (Files.isDirectory(child)) {
                            try {
                                registerTree(child);
                            } catch (IOException e) {
                                // folder vanished between create and register — nothing to watch
                            }
                        } else if (MediaScanner.isMedia(child)) {
                            markPending(child);
                        }
                    }
                }
                if (overflow) {
                    rescanAll();
                }
                key.reset();
            }
            List<Path> settled = settledNow();
            if (!settled.isEmpty()) {
                callback.accept(settled);
            }
        }
    }

    public void stop() {
        running = false;
    }

    @Override
    public void close() throws IOException {
        stop();
        watchService.close();
    }

    private void registerTree(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path d : walk.filter(Files::isDirectory).toList()) {
                WatchKey key = d.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                keys.put(key, d);
            }
        }
    }

    private void rescanAll() {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).filter(MediaScanner::isMedia)
                    .filter(p -> !handedOver.contains(p))
                    .forEach(this::markPending);
        } catch (IOException e) {
            // root disappeared; the next settle pass simply finds nothing
        }
    }

    private void markPending(Path file) {
        pending.put(file, new long[]{sizeOf(file), System.currentTimeMillis()});
    }

    /** Files whose size has been unchanged for the settle window are considered complete. */
    private List<Path> settledNow() {
        List<Path> ready = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Path file = entry.getKey();
            long[] state = entry.getValue();
            long size = sizeOf(file);
            if (size < 0) {
                it.remove(); // deleted or renamed away before it settled
                continue;
            }
            if (size != state[0]) {
                state[0] = size;
                state[1] = now;
                continue;
            }
            if (now - state[1] >= settleMillis) {
                it.remove();
                handedOver.add(file);
                ready.add(file);
            }
        }
        return ready;
    }

    private static long sizeOf(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : -1;
        } catch (IOException e) {
            return -1;
        }
    }
}
