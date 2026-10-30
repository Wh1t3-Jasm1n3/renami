package app.renami.cli;

import app.renami.media.MediaScanner;
import app.renami.parse.FileNameParser;
import app.renami.parse.ReleaseInfo;
import app.renami.script.ScriptRuntime;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * Select files with an inline Groovy predicate — the equivalent of the
 * {@code --filter} expression of GUI filter modes.
 */
@Command(name = "filter", description = "Select media files with a Groovy predicate expression.", mixinStandardHelpOptions = true)
public class FilterCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "TARGET", description = "folder (or single file) to filter")
    Path target;

    @Option(names = {"--where", "--filter"}, required = true, paramLabel = "EXPR",
            description = "Groovy expression, e.g. \"year >= 2010 && fn.name =~ /1080p/\"")
    String expression;

    @Option(names = "--not", description = "invert the predicate (select non-matches)")
    boolean invert;

    @Option(names = "--def", description = "define script variable: key=value")
    List<String> definitions = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        List<Path> files;
        if (Files.isRegularFile(target)) {
            files = List.of(target);
        } else if (Files.isDirectory(target)) {
            files = MediaScanner.scan(target);
        } else {
            System.err.println("not found: " + target);
            return 1;
        }

        Predicate<Map<String, Object>> predicate =
                new ScriptRuntime(ScriptRuntime.parseDefs(definitions)).compilePredicate(expression);

        int matched = 0;
        for (Path file : files) {
            boolean hit = predicate.test(bindingFor(file));
            if (hit != invert) {
                System.out.println(file);
                matched++;
            }
        }
        System.err.println(matched + " of " + files.size() + " files matched");
        return 0;
    }

    /** Script bindings: fn, name, ext, size, plus parsed facts title/year/season/episode/absolute. */
    static Map<String, Object> bindingFor(Path file) throws Exception {
        ReleaseInfo info = FileNameParser.parse(file.getFileName().toString(), MediaScanner.extension(file));
        Map<String, Object> vars = new HashMap<>();
        vars.put("fn", file.toFile());
        vars.put("info", info);
        vars.put("name", file.getFileName().toString());
        vars.put("ext", MediaScanner.extension(file));
        vars.put("size", Files.size(file));
        vars.put("title", info.titleGuess());
        vars.put("year", info.year());
        vars.put("season", info.hasEpisode() ? Integer.valueOf(info.episode().season()) : null);
        vars.put("episode", info.hasEpisode() ? Integer.valueOf(info.episode().episode()) : info.absoluteEpisode());
        return vars;
    }
}
