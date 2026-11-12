package app.renami.cli;

import app.renami.tui.TuiApp;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "tui", description = "Full-screen terminal UI with every mode as a tab.",
        mixinStandardHelpOptions = true)
public class TuiCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        return new TuiApp().run();
    }
}
