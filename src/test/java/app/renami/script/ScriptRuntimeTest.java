package app.renami.script;

import app.renami.parse.FileNameParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptRuntimeTest {

    @TempDir
    Path dir;

    @Test
    void scriptSeesFnInfoAndDefVariables() throws Exception {
        Path file = Files.writeString(dir.resolve("the.file.mkv"), "x");
        Path script = Files.writeString(dir.resolve("hook.groovy"), """
                new File(outFile).text = "fn=${fn.name} title=${info.titleGuess()} ep=${info.episode().format()} my=${greeting}"
                """);

        var runtime = new ScriptRuntime(Map.of("outFile", dir.resolve("log.txt").toString(), "greeting", "hello"));
        runtime.run(script, Map.of(
                "fn", file.toFile(),
                "info", FileNameParser.parse("the.file.s01e02.mkv", "mkv")));

        String log = Files.readString(dir.resolve("log.txt"));
        assertEquals("fn=the.file.mkv title=the file ep=S01E02 my=hello", log);
    }

    @Test
    void renamiHelperCanRenameFromScript() throws Exception {
        Path file = Files.writeString(dir.resolve("old.mkv"), "x");
        Path script = Files.writeString(dir.resolve("rename.groovy"), """
                renami.rename(fn, "new.mkv")
                """);

        new ScriptRuntime(Map.of()).run(script, Map.of("fn", file.toFile()));
        assertTrue(Files.notExists(file));
        assertTrue(Files.isRegularFile(dir.resolve("new.mkv")));
    }

    @Test
    void parseDefsSplitsOnFirstEquals() {
        var defs = ScriptRuntime.parseDefs(java.util.List.of("a=1", "b=x=y", "bad"));
        assertEquals(Map.of("a", "1", "b", "x=y"), defs);
    }
}
