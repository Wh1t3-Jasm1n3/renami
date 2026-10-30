package app.renami;

import app.renami.cli.ProbeCommand;
import app.renami.cli.RenameCommand;
import app.renami.cli.UndoCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "renami",
        version = "Renami 0.1.0",
        mixinStandardHelpOptions = true,
        description = "Open-source media file renamer and organizer.",
        subcommands = {RenameCommand.class, UndoCommand.class, ProbeCommand.class}
)
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
