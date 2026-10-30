package app.renami;

import app.renami.cli.ChecksumCommand;
import app.renami.cli.DupeCommand;
import app.renami.cli.ExtractCommand;
import app.renami.cli.GuiCommand;
import app.renami.cli.ListCommand;
import app.renami.cli.PostProcessCommand;
import app.renami.cli.ProbeCommand;
import app.renami.cli.RenameCommand;
import app.renami.cli.SubtitlesCommand;
import app.renami.cli.TrackCommand;
import app.renami.cli.UndoCommand;
import app.renami.gui.RenamiFrame;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
        name = "renami",
        version = "Renami",
        mixinStandardHelpOptions = true,
        description = "Open-source media file renamer and organizer.",
        subcommands = {RenameCommand.class, UndoCommand.class, ProbeCommand.class,
                ChecksumCommand.class, DupeCommand.class, ExtractCommand.class, ListCommand.class,
                PostProcessCommand.class, GuiCommand.class, SubtitlesCommand.class, TrackCommand.class}
)
public final class Main implements Callable<Integer> {

    private Main() {
    }

    /** Bare `renami` opens the desktop UI, like the classic media renamers. */
    @Override
    public Integer call() {
        return RenamiFrame.open();
    }

    public static void main(String[] args) {
        CommandLine cli = new CommandLine(new Main());
        cli.getCommandSpec().versionProvider(() -> new String[]{app.renami.util.Version.full()});
        System.exit(cli.execute(args));
    }
}
