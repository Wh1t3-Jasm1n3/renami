package app.renami.cli;

import app.renami.hash.PartialHasher;
import app.renami.hash.HashType;
import com.google.gson.GsonBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Command(name = "dupe", description = "Find duplicate files (size → partial hash → full SHA-256).", mixinStandardHelpOptions = true)
public class DupeCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "FOLDER", description = "folder to scan recursively")
    Path folder;

    @Option(names = "--json", description = "machine-readable output")
    boolean json;

    @Override
    public Integer call() throws Exception {
        List<List<Path>> groups = findDuplicates(folder);
        if (json) {
            List<List<String>> out = groups.stream().map(g -> g.stream().map(Path::toString).toList()).toList();
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(out));
        } else {
            if (groups.isEmpty()) {
                System.out.println("no duplicates found");
            }
            int index = 1;
            for (List<Path> group : groups) {
                System.out.println("group " + index++ + " (" + group.size() + " copies):");
                for (Path p : group) {
                    System.out.println("  " + p);
                }
            }
        }
        return 0;
    }

    /** Three-phase scan: never hash more bytes than necessary. */
    public static List<List<Path>> findDuplicates(Path root) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile).toList();
        }

        Map<Long, List<Path>> bySize = new TreeMap<>();
        for (Path f : files) {
            bySize.computeIfAbsent(Files.size(f), k -> new ArrayList<>()).add(f);
        }

        Map<String, List<Path>> byPartial = new LinkedHashMap<>();
        for (List<Path> sizeGroup : bySize.values()) {
            if (sizeGroup.size() < 2) {
                continue;
            }
            for (Path f : sizeGroup) {
                byPartial.computeIfAbsent(PartialHasher.partialSha256(f), k -> new ArrayList<>()).add(f);
            }
        }

        Map<String, List<Path>> byFull = new LinkedHashMap<>();
        for (List<Path> partialGroup : byPartial.values()) {
            if (partialGroup.size() < 2) {
                continue;
            }
            for (Path f : partialGroup) {
                byFull.computeIfAbsent(HashType.SHA256.hex(f), k -> new ArrayList<>()).add(f);
            }
        }

        List<List<Path>> groups = new ArrayList<>();
        for (List<Path> group : byFull.values()) {
            if (group.size() >= 2) {
                group.sort(Comparator.comparing(Path::toString));
                groups.add(List.copyOf(group));
            }
        }
        groups.sort(Comparator.comparing(g -> g.get(0).toString()));
        return groups;
    }
}
