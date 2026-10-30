package app.renami.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveToolTest {

    @TempDir
    Path dir;

    @Test
    void recognizesArchiveExtensions() {
        assertTrue(ArchiveTool.isArchive(Path.of("a.zip")));
        assertTrue(ArchiveTool.isArchive(Path.of("a.7z")));
        assertTrue(ArchiveTool.isArchive(Path.of("a.tar.gz")));
        assertTrue(ArchiveTool.isArchive(Path.of("a.mkv.zip")));
        assertEquals(false, ArchiveTool.isArchive(Path.of("movie.mkv")));
    }

    @Test
    void listsAndExtractsZipWithNativeUnzip() throws Exception {
        Path zip = createZip();
        List<String> entries = ArchiveTool.list(zip);
        assertTrue(entries.stream().anyMatch(e -> e.endsWith("movie.mkv")), entries.toString());

        Path out = dir.resolve("out");
        ArchiveTool.extract(zip, out);
        assertTrue(Files.isRegularFile(out.resolve("movie.mkv")));
        assertEquals("payload", Files.readString(out.resolve("movie.mkv")));
    }

    private Path createZip() throws Exception {
        Path zip = dir.resolve("sample.zip");
        try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new java.util.zip.ZipEntry("movie.mkv"));
            out.write("payload".getBytes());
            out.closeEntry();
        }
        return zip;
    }
}
