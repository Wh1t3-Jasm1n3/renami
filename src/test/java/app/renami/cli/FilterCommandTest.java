package app.renami.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterCommandTest {

    @TempDir
    Path dir;

    private String run(FilterCommand cmd) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            assertEquals(0, cmd.call());
        } finally {
            System.setOut(original);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void selectsFilesByParsedYear() throws Exception {
        Files.writeString(dir.resolve("Inception.2010.1080p.mkv"), "x");
        Files.writeString(dir.resolve("Blade.Runner.1982.mkv"), "x");

        FilterCommand cmd = new FilterCommand();
        cmd.target = dir;
        cmd.expression = "year >= 2010";
        List<String> lines = run(cmd).lines().toList();
        assertEquals(List.of(dir.resolve("Inception.2010.1080p.mkv").toString()), lines);
    }

    @Test
    void supportsRegexOnFilenameAndInversion() throws Exception {
        Files.writeString(dir.resolve("Movie.2020.1080p.mkv"), "x");
        Files.writeString(dir.resolve("Movie.2020.480p.mkv"), "x");

        FilterCommand cmd = new FilterCommand();
        cmd.target = dir;
        cmd.expression = "fn.name =~ /1080p/";
        cmd.invert = true;
        String out = run(cmd);
        assertTrue(out.contains("480p"));
        assertTrue(!out.contains("1080p"));
    }

    @Test
    void defVariablesAreVisible() throws Exception {
        Files.writeString(dir.resolve("Movie.2020.mkv"), "x");
        Files.writeString(dir.resolve("Movie.2021.mkv"), "x");

        FilterCommand cmd = new FilterCommand();
        cmd.target = dir;
        cmd.expression = "year >= minYear";
        cmd.definitions = List.of("minYear=2021");
        String out = run(cmd);
        assertTrue(out.contains("2021"));
        assertTrue(!out.contains("2020"));
    }
}
