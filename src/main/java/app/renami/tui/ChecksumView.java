package app.renami.tui;

import app.renami.hash.HashType;
import app.renami.media.MediaScanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The Checksum tab: SHA-256 of every media file under a path. */
public final class ChecksumView extends PathTaskView {

    public ChecksumView() {
        super("Enter = SHA-256 every media file (sidecar files via `renami checksum`)");
    }

    @Override
    public String title() {
        return "Checksum";
    }

    @Override
    protected List<String> compute(Path input) throws Exception {
        List<Path> files = Files.isRegularFile(input)
                ? List.of(input) : MediaScanner.scan(input);
        List<String> out = new ArrayList<>();
        for (Path file : files) {
            out.add(HashType.SHA256.hex(file) + "  " + input.relativize(file));
            setStatus("hashed " + out.size() + "/" + files.size() + " ...");
        }
        return out;
    }
}
