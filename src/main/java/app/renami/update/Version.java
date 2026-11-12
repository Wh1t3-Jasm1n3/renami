package app.renami.update;

import java.util.Arrays;

/** Release version like "1.6.0" — numeric segments, tolerant of a leading "v" and suffixes. */
public record Version(int[] parts) implements Comparable<Version> {

    public static Version parse(String raw) {
        if (raw == null) {
            return new Version(new int[0]);
        }
        String s = raw.trim().toLowerCase().replaceFirst("^v", "");
        int dash = s.indexOf('-'); // "1.6.1-beta1" → compare on "1.6.1"
        if (dash >= 0) {
            s = s.substring(0, dash);
        }
        String[] segs = s.split("\\.");
        int[] parts = new int[segs.length];
        for (int i = 0; i < segs.length; i++) {
            try {
                parts[i] = Integer.parseInt(segs[i].replaceAll("\\D.*", ""));
            } catch (NumberFormatException e) {
                parts[i] = 0;
            }
        }
        return new Version(parts);
    }

    @Override
    public int compareTo(Version other) {
        int n = Math.max(parts.length, other.parts.length);
        for (int i = 0; i < n; i++) {
            int a = i < parts.length ? parts[i] : 0;
            int b = i < other.parts.length ? other.parts[i] : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    public boolean isNewerThan(Version current) {
        return compareTo(current) > 0;
    }

    @Override
    public String toString() {
        return String.join(".", Arrays.stream(parts).mapToObj(String::valueOf).toArray(String[]::new));
    }
}
