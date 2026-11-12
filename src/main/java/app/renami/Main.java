package app.renami;

import app.renami.cli.ChecksumCommand;
import app.renami.cli.DupeCommand;
import app.renami.cli.ExtractCommand;
import app.renami.cli.FilterCommand;
import app.renami.cli.GuiCommand;
import app.renami.cli.ListCommand;
import app.renami.cli.PostProcessCommand;
import app.renami.cli.ProbeCommand;
import app.renami.cli.RenameCommand;
import app.renami.cli.SubtitlesCommand;
import app.renami.cli.TrackCommand;
import app.renami.cli.UndoCommand;
import app.renami.cli.UpdateCommand;
import app.renami.cli.TuiCommand;
import app.renami.cli.WatchCommand;
import app.renami.gui.RenamiFrame;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "renami",
        version = "Renami",
        mixinStandardHelpOptions = true,
        description = "Open-source media file renamer and organizer.",
        subcommands = {RenameCommand.class, UndoCommand.class, ProbeCommand.class,
                ChecksumCommand.class, DupeCommand.class, ExtractCommand.class, ListCommand.class,
                FilterCommand.class, PostProcessCommand.class, GuiCommand.class, SubtitlesCommand.class,
                TrackCommand.class, WatchCommand.class, UpdateCommand.class, TuiCommand.class}
)
public final class Main implements Callable<Integer> {

    @Parameters(index = "0..*", paramLabel = "FILE", arity = "0..*", hidden = true,
            description = "files opened via Finder (opens the GUI preloaded)")
    List<Path> openedFiles = List.of();

    private Main() {
    }

    /** Bare `renami` (or Finder open-with) opens the desktop UI. */
    @Override
    public Integer call() {
        for (Path file : openedFiles) {
            if (!java.nio.file.Files.exists(file)) {
                System.err.println("error: not found: " + file
                        + " (did you mean a subcommand? run `renami --help`)");
                return 2;
            }
        }
        return RenamiFrame.open(openedFiles);
    }

    public static void main(String[] args) {
        CommandLine cli = new CommandLine(new Main());
        cli.getCommandSpec().versionProvider(() -> new String[]{app.renami.util.Version.full()});
        // provider/network failures are user-fixable: report them as a message, not a stack trace
        cli.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            if (ex instanceof java.io.IOException) {
                System.err.println("error: " + ex.getMessage());
                return 1;
            }
            ex.printStackTrace(System.err);
            return 1;
        });
        System.exit(cli.execute(args));
    }
}
