package app.renami.watch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolderWatcherTest {

    @TempDir
    Path dir;

    private Thread start(FolderWatcher watcher, java.util.function.Consumer<List<Path>> callback) {
        Thread t = new Thread(() -> {
            try {
                watcher.awaitBatches(callback);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    @Test
    void reportsNewMediaFileOnceSettled() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Path>> got = new AtomicReference<>();
        try (FolderWatcher watcher = new FolderWatcher(dir, Duration.ofMillis(300))) {
            start(watcher, batch -> {
                got.set(batch);
                latch.countDown();
            });
            Thread.sleep(200); // let the watch loop come up
            Files.writeString(dir.resolve("movie.mkv"), "payload");

            assertTrue(latch.await(10, TimeUnit.SECONDS), "new media file must be reported");
            assertEquals(List.of(dir.resolve("movie.mkv")), got.get());
        }
    }

    @Test
    void ignoresExistingFilesAndNonMedia() throws Exception {
        Files.writeString(dir.resolve("existing.mkv"), "was here before watching started");
        CountDownLatch latch = new CountDownLatch(1);
        try (FolderWatcher watcher = new FolderWatcher(dir, Duration.ofMillis(300))) {
            start(watcher, batch -> latch.countDown());
            Thread.sleep(200);
            Files.writeString(dir.resolve("notes.txt"), "not media");

            assertTrue(!latch.await(2, TimeUnit.SECONDS), "nothing should be reported yet");

            Files.writeString(dir.resolve("movie.mkv"), "x");
            assertTrue(latch.await(10, TimeUnit.SECONDS), "media file must be reported");
        }
    }

    @Test
    void growingFileIsHandedOverExactlyOnceAfterItStopsGrowing() throws Exception {
        List<List<Path>> batches = new CopyOnWriteArrayList<>();
        try (FolderWatcher watcher = new FolderWatcher(dir, Duration.ofMillis(600))) {
            start(watcher, batches::add);
            Thread.sleep(200);
            Path part = dir.resolve("download.mkv");
            Files.writeString(part, "chunk1");
            Thread.sleep(250);
            Files.writeString(part, "chunk1chunk2");
            Thread.sleep(250);
            Files.writeString(part, "chunk1chunk2chunk3");

            long deadline = System.currentTimeMillis() + 10_000;
            while (batches.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            assertEquals(1, batches.size(), "one settled batch");
            assertEquals(List.of(part), batches.get(0));
            Thread.sleep(1_200); // well past the settle window
            assertEquals(1, batches.size(), "no duplicate handover");
            assertEquals("chunk1chunk2chunk3", Files.readString(part));
        }
    }
}
