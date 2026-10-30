package app.renami.cli;

import app.renami.archive.ArchiveTool;
import app.renami.media.MediaScanner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "list", description = "List media files in a folder or entries of an archive.", mixinStandardHelpOptions = true)
public class ListCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "TARGET", description = "folder or archive file")
    Path target;

    @Override
    public Integer call() throws Exception {
        if (ArchiveTool.isArchive(target)) {
            for (String entry : ArchiveTool.list(target)) {
                System.out.println(entry);
            }
            return 0;
        }
        if (!Files.exists(target)) {
            System.err.println("not found: " + target);
            return 1;
        }
        List<Path> files = MediaScanner.scan(target);
        files.forEach(System.out::println);
        System.err.println(files.size() + " media files");
        return 0;
    }
}
