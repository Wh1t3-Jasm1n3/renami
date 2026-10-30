package app.renami.cli;

import app.renami.archive.ArchiveTool;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "extract", description = "Extract archives (zip/tar/7z/rar via native tools).", mixinStandardHelpOptions = true)
public class ExtractCommand implements Callable<Integer> {

    @Parameters(index = "0..*", paramLabel = "ARCHIVE", description = "archive file(s)")
    List<Path> archives;

    @Option(names = {"-o", "--out"}, description = "output directory (default: <archive-name>/ next to the archive)")
    Path out;

    @Override
    public Integer call() throws Exception {
        for (Path archive : archives) {
            if (!ArchiveTool.isArchive(archive)) {
                System.err.println("skip (not a recognized archive): " + archive);
                continue;
            }
            Path target = out != null
                    ? out
                    : archive.toAbsolutePath().getParent().resolve(stripExtension(archive));
            ArchiveTool.extract(archive, target);
            System.out.println("extracted " + archive.getFileName() + "  →  " + target);
        }
        return 0;
    }

    private static String stripExtension(Path archive) {
        String name = archive.getFileName().toString();
        for (String compound : List.of(".tar.gz", ".tar.bz2", ".tar.xz")) {
            if (name.toLowerCase().endsWith(compound)) {
                return name.substring(0, name.length() - compound.length());
            }
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
