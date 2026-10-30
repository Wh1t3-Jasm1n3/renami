package app.renami.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiCommandTest {

    @Test
    void bindsPreloadFilesFromArguments() {
        GuiCommand cmd = new GuiCommand();
        CommandLine.ParseResult parsed = new CommandLine(cmd)
                .parseArgs("movie.mkv", "/some folder/show.mkv");
        assertEquals(0, parsed.errors().size());
        assertEquals(2, cmd.preload.size());
        assertEquals(Path.of("movie.mkv"), cmd.preload.get(0));
        assertEquals(Path.of("/some folder/show.mkv"), cmd.preload.get(1));
    }

    @Test
    void noArgumentsMeansEmptyPreload() {
        GuiCommand cmd = new GuiCommand();
        new CommandLine(cmd).parseArgs();
        assertEquals(0, cmd.preload.size());
    }
}
