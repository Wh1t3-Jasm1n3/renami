package app.renami.cli;

import app.renami.gui.RenamiFrame;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "gui", description = "Open the desktop rename panel.", mixinStandardHelpOptions = true)
public class GuiCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return RenamiFrame.open();
    }
}
