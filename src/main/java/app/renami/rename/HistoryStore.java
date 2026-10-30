package app.renami.rename;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Append-only JSONL journal of rename batches in ~/.renami/history.jsonl.
 * Each batch: {"id":…,"time":…,"undone":false,"ops":[["from","to","verb"],…]}
 * (journals written before 1.1 store two-element ops and are read as "renamed").
 */
public final class HistoryStore {

    public record Batch(String id, long time, boolean undone, List<String[]> ops) {
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path file;

    public HistoryStore() {
        this(app.renami.util.Config.dataDir().resolve("history.jsonl"));
    }

    public HistoryStore(Path file) {
        this.file = file;
    }

    public void record(List<RenameOperation> applied) throws IOException {
        record(applied, TransferAction.RENAME);
    }

    public void record(List<RenameOperation> applied, TransferAction action) throws IOException {
        Files.createDirectories(file.getParent());
        List<String[]> ops = new ArrayList<>();
        for (RenameOperation op : applied) {
            ops.add(new String[]{op.source().toString(), op.target().toString(), action.verb()});
        }
        JsonObject batch = new JsonObject();
        batch.addProperty("id", UUID.randomUUID().toString());
        batch.addProperty("time", System.currentTimeMillis());
        batch.addProperty("undone", false);
        batch.add("ops", GSON.toJsonTree(ops));
        Files.writeString(file, GSON.toJson(batch) + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** @return the most recent batch that has not been undone yet (latest record per id wins) */
    public Optional<Batch> lastActiveBatch() throws IOException {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Set<String> seen = new HashSet<>();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            String id = o.get("id").getAsString();
            if (!seen.add(id)) {
                continue; // a newer record for this batch was already processed
            }
            if (!o.get("undone").getAsBoolean()) {
                List<String[]> ops = new ArrayList<>();
                o.getAsJsonArray("ops").forEach(e -> {
                    var arr = e.getAsJsonArray();
                    String verb = arr.size() > 2 ? arr.get(2).getAsString() : "renamed";
                    ops.add(new String[]{arr.get(0).getAsString(), arr.get(1).getAsString(), verb});
                });
                return Optional.of(new Batch(id, o.get("time").getAsLong(), false, ops));
            }
        }
        return Optional.empty();
    }

    /** Marks a batch as undone by appending a copy with undone=true (last record per id wins). */
    public void markUndone(String batchId) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            if (o.get("id").getAsString().equals(batchId)) {
                o.addProperty("undone", true);
                Files.writeString(file, GSON.toJson(o) + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return;
            }
        }
    }
}
