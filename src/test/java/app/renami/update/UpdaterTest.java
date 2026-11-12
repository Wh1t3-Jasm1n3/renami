package app.renami.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdaterTest {

    private static Path fakeApp(Path dir, String marker) throws IOException {
        Path app = dir.resolve("Renami.app");
        Files.createDirectories(app.resolve("Contents/MacOS"));
        Files.createDirectories(app.resolve("Contents/app"));
        Path launcher = app.resolve("Contents/MacOS/Renami");
        Files.writeString(launcher, "#!/bin/sh\necho " + marker + "\n");
        Files.setPosixFilePermissions(launcher,
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ));
        Files.writeString(app.resolve("Contents/app/renami.jar"), marker + " jar");
        return app;
    }

    @Test
    void swapReplacesBundleAndRemovesOldCopy(@TempDir Path dir) throws IOException {
        Path target = fakeApp(dir.resolve("old"), "old");
        Path fresh = fakeApp(dir.resolve("new"), "new");

        Updater.swap(fresh, target);

        assertEquals("new jar", Files.readString(target.resolve("Contents/app/renami.jar")));
        assertFalse(Files.exists(target.resolveSibling("Renami.app.old"))); // old copy removed
    }

    @Test
    void copyTreeKeepsSymlinksAndExecBits(@TempDir Path dir) throws IOException {
        Path source = fakeApp(dir.resolve("src"), "src");
        Files.createDirectories(source.resolve("Contents/Frameworks"));
        Path link = source.resolve("Contents/Frameworks/liblib");
        Files.createSymbolicLink(link, Path.of("../app"));

        Path dest = dir.resolve("dst");
        Updater.copyTree(source, dest);

        Path copiedLink = dest.resolve("Contents/Frameworks/liblib");
        assertTrue(Files.isSymbolicLink(copiedLink));
        assertEquals(Path.of("../app"), Files.readSymbolicLink(copiedLink));
        Set<PosixFilePermission> mode =
                Files.getPosixFilePermissions(dest.resolve("Contents/MacOS/Renami"));
        assertTrue(mode.contains(PosixFilePermission.OWNER_EXECUTE));
    }

    @Test
    void failedCopyRollsBackToTheOldBundle(@TempDir Path dir) throws IOException {
        Path target = fakeApp(dir.resolve("old"), "old");
        Path fresh = fakeApp(dir.resolve("new"), "new");
        // an unreadable file makes staging fail before the running app is ever touched
        Path blocked = fresh.resolve("Contents/app/renami.jar");
        Files.setPosixFilePermissions(blocked, Set.of());

        boolean failed = false;
        try {
            Updater.swap(fresh, target);
        } catch (IOException expected) {
            failed = true;
        }
        assertTrue(failed, "swap should fail on an unreadable payload");
        // the user is never left without an app: the old bundle is back in place
        assertEquals("old jar", Files.readString(target.resolve("Contents/app/renami.jar")));
    }
}
