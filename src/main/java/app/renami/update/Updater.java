package app.renami.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Set;

/**
 * One-click update: download the release zip, unpack it, and swap the running
 * {@code Renami.app} for the new one — the old copy is removed only after the
 * new bundle is fully in place (a failed copy rolls back).
 */
public final class Updater {

    private Updater() {
    }

    /** The {@code Renami.app} this process runs from, or null for dev/CLI builds. */
    public static Path installedApp() {
        String exe = System.getProperty("jpackage.app-path");
        if (exe == null) {
            return null;
        }
        Path path = Path.of(exe).toAbsolutePath();
        while (path != null && !path.getFileName().toString().endsWith(".app")) {
            path = path.getParent();
        }
        return path;
    }

    /** Downloads and verifies the new bundle, then swaps it in. Returns the updated app path. */
    public static Path install(String zipUrl, Path targetApp) throws IOException, InterruptedException {
        Path tmp = Files.createTempDirectory("renami-update");
        try {
            Path zip = tmp.resolve("release.zip");
            download(zipUrl, zip);
            Path unpacked = tmp.resolve("unpacked");
            Files.createDirectories(unpacked);
            // /usr/bin/unzip keeps the bundle's symlinks and exec bits, java.util.zip does not
            Process unzip = new ProcessBuilder("/usr/bin/unzip", "-q", zip.toString(),
                    "-d", unpacked.toString()).inheritIO().start();
            if (unzip.waitFor() != 0) {
                throw new IOException("unzipping the update failed");
            }
            Path newApp = unpacked.resolve("Renami.app");
            if (!Files.isDirectory(newApp)) {
                throw new IOException("update zip did not contain Renami.app");
            }
            swap(newApp, targetApp);
            return targetApp;
        } finally {
            deleteTree(tmp);
        }
    }

    /**
     * Stage the new bundle next to the old one, then swap by rename: the running app is
     * only ever absent for the instant between two renames, and any failure leaves the
     * old bundle exactly where it was.
     */
    static void swap(Path newApp, Path targetApp) throws IOException {
        Path staged = targetApp.resolveSibling(targetApp.getFileName() + ".new");
        Path old = targetApp.resolveSibling(targetApp.getFileName() + ".old");
        deleteTree(staged);
        copyTree(newApp, staged);
        deleteTree(old);
        Files.move(targetApp, old, StandardCopyOption.ATOMIC_MOVE);
        try {
            Files.move(staged, targetApp, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            Files.move(old, targetApp); // roll back so the user is never left without an app
            throw failure;
        }
        try {
            deleteTree(old);
        } catch (IOException stale) {
            // the update is already in place; a leftover old copy must not fail the install
        }
    }

    static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path dest = to.resolve(from.relativize(dir).toString());
                Files.createDirectories(dest);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path dest = to.resolve(from.relativize(file).toString());
                if (Files.isSymbolicLink(file)) {
                    Files.createSymbolicLink(dest, Files.readSymbolicLink(file));
                } else {
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                    Set<PosixFilePermission> mode = Files.getPosixFilePermissions(file);
                    Files.setPosixFilePermissions(dest, mode); // keep the launcher exec bits
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void download(String url, Path into) throws IOException, InterruptedException {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build();
        HttpResponse<Path> response = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofFile(into));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
        }
    }

    /**
     * Spawns the relaunch helper BEFORE the bundle is swapped. Once the running app's
     * own bundle has been replaced in place, this JVM can no longer spawn processes —
     * its jspawnhelper was replaced mid-flight ("Failed to exec spawn helper") — so the
     * helper must already be running. It waits for this process to exit, then reopens
     * the updated bundle through LaunchServices; opening while the old instance is
     * alive would just activate the dying process. Destroy the returned process to
     * cancel (e.g. when the install fails).
     */
    public static Process scheduleRestart(Path appPath) throws IOException {
        long pid = ProcessHandle.current().pid();
        return new ProcessBuilder("/bin/sh", "-c",
                "while kill -0 " + pid + " 2>/dev/null; do sleep 0.2; done; "
                        + "open " + shellQuote(appPath.toString()))
                .start();
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
