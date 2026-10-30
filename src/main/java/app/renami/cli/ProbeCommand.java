package app.renami.cli;

import app.renami.media.FFProbeCliProbe;
import app.renami.media.MediaInfoCliProbe;
import app.renami.media.MediaProbe;
import com.google.gson.Gson;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(name = "probe", description = "Dump technical metadata (MediaInfo or ffprobe JSON).", mixinStandardHelpOptions = true)
public class ProbeCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "FILE", description = "media file to probe")
    Path file;

    @Override
    public Integer call() throws Exception {
        List<MediaProbe> probes = List.of(new MediaInfoCliProbe(), new FFProbeCliProbe());
        Optional<MediaProbe> probe = probes.stream().filter(MediaProbe::isAvailable).findFirst();
        if (probe.isEmpty()) {
            System.err.println("error: neither `mediainfo` nor `ffprobe` is installed (brew install media-info ffmpeg)");
            return 2;
        }
        Optional<com.google.gson.JsonObject> json = probe.get().probe(file);
        if (json.isEmpty()) {
            System.err.println("error: probe returned no data for " + file);
            return 1;
        }
        System.out.println(new Gson().newBuilder().setPrettyPrinting().create().toJson(json.get()));
        return 0;
    }
}
