package app.renami.hash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reading and writing checksum sidecar files.
 * Supports both classic SFV ({@code name md5}) and md5sum-style ({@code md5  name}) formats.
 */
public final class VerificationFile {

    public record Entry(String name, String hash) {
    }

    private VerificationFile() {
    }

    public static List<Entry> read(Path file) throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) {
                continue; // comments and blanks
            }
            String[] tokens = trimmed.split("\\s+", 2);
            if (tokens.length < 2) {
                continue;
            }
            String first = tokens[0];
            String rest = tokens[1].trim();
            if (isHex(first)) {
                // hash-first format: "md5sum-style: hash  name" (strip optional binary-mode marker)
                String name = rest.startsWith("*") ? rest.substring(1) : rest;
                entries.add(new Entry(name, first.toLowerCase(Locale.ROOT)));
            } else if (isHex(rest)) {
                // SFV format: "name hash"
                entries.add(new Entry(first, rest.toLowerCase(Locale.ROOT)));
            }
        }
        return entries;
    }

    public static void write(Path file, List<Entry> entries) throws IOException {
        write(file, entries, null);
    }

    public static void write(Path file, List<Entry> entries, HashType algorithm) throws IOException {
        StringBuilder out = new StringBuilder();
        if (algorithm != null) {
            // header lets --verify pick the right algorithm even when digests are ambiguous (MD5 vs ED2K)
            out.append("; algorithm=").append(algorithm.name().toLowerCase(Locale.ROOT)).append('\n');
        }
        String ext = extension(file);
        boolean sfv = ext.equals("sfv");
        for (Entry entry : entries) {
            if (sfv) {
                out.append(entry.name()).append(' ').append(entry.hash().toUpperCase(Locale.ROOT));
            } else {
                out.append(entry.hash()).append("  ").append(entry.name());
            }
            out.append('\n');
        }
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
    }

    /** Algorithm declared in a sidecar header comment, or null when absent. */
    public static HashType readAlgorithm(Path file) throws IOException {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!(trimmed.startsWith(";") || trimmed.startsWith("#"))) {
                if (!trimmed.isEmpty()) {
                    return null; // header section ended
                }
                continue;
            }
            String body = trimmed.substring(1).trim();
            if (body.startsWith("algorithm=")) {
                try {
                    return HashType.parse(body.substring("algorithm=".length()));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Default algorithm for a sidecar extension. */
    public static HashType defaultAlgorithm(Path file, HashType fallback) {
        return switch (extension(file)) {
            case "sfv", "md5" -> HashType.MD5;
            case "ed2k" -> HashType.ED2K;
            case "sha1" -> HashType.SHA1;
            case "sha256", "sha" -> HashType.SHA256;
            case "crc" -> HashType.CRC32;
            default -> fallback;
        };
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isHex(String s) {
        if (s.length() < 8 || s.length() > 64) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }
}
