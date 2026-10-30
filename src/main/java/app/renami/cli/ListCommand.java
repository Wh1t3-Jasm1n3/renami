package app.renami.cli;

import app.renami.archive.ArchiveTool;
import app.renami.format.FormatEngine;
import app.renami.media.MediaInfoVars;
import app.renami.media.MediaProbe;
import app.renami.media.MediaScanner;
import app.renami.parse.FileNameParser;
import app.renami.parse.ReleaseInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(name = "list", description = "List media files in a folder or entries of an archive.", mixinStandardHelpOptions = true)
public class ListCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "TARGET", description = "folder or archive file")
    Path target;

    @Option(names = "--format", paramLabel = "EXPR",
            description = "render one line per file, e.g. \"{f} | {n} ({y}) | {vres} | {ac}\"")
    String format;

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
        if (format == null) {
            files.forEach(System.out::println);
            System.err.println(files.size() + " media files");
            return 0;
        }
        Optional<MediaProbe> probe = List.of(
                        new app.renami.media.MediaInfoCliProbe(), new app.renami.media.FFProbeCliProbe())
                .stream().filter(MediaProbe::isAvailable).findFirst();
        for (Path file : files) {
            System.out.println(FormatEngine.format(format, varsFor(file, probe)));
        }
        return 0;
    }

    static Map<String, Object> varsFor(Path file, Optional<MediaProbe> probe) throws Exception {
        ReleaseInfo info = FileNameParser.parse(file.getFileName().toString(), MediaScanner.extension(file));
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("f", file.getFileName().toString());
        vars.put("path", file.toString());
        vars.put("ext", MediaScanner.extension(file));
        vars.put("n", info.titleGuess());
        vars.put("y", info.year() == 0 ? "" : info.year());
        if (info.hasEpisode()) {
            vars.put("s00e00", info.episode().format());
            vars.put("s", info.episode().season());
            vars.put("e", info.episode().episode());
        }
        if (info.hasAbsoluteEpisode()) {
            vars.put("absolute", info.absoluteEpisode());
        }
        probe.ifPresent(p -> {
            try {
                p.probe(file).ifPresent(json -> vars.putAll(MediaInfoVars.extract(json)));
            } catch (Exception ignored) {
                // per-file probe failures just leave the media vars empty
            }
        });
        return vars;
    }
}
