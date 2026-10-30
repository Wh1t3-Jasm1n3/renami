package app.renami.cli;

import app.renami.gui.RenamiFrame;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "gui", description = "Open the desktop rename panel.", mixinStandardHelpOptions = true)
public class GuiCommand implements Callable<Integer> {

    @Parameters(index = "0..*", paramLabel = "FILE", arity = "0..*",
            description = "files or folders to preload (set by Finder open-with)")
    List<Path> preload = List.of();

    @Override
    public Integer call() {
        return RenamiFrame.open(preload);
    }
}
