package app.renami.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Configuration from ~/.renami/config.properties with environment variable overrides. */
public final class Config {

    private final Properties props = new Properties();

    private Config() {
        Path file = dataDir().resolve("config.properties");
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("warning: cannot read " + file + ": " + e.getMessage());
            }
        }
    }

    public static Path dataDir() {
        return Path.of(System.getProperty("user.home"), ".renami");
    }

    /** Lookup order: env var (RENAMI_FOO_BAR) → config property (foo.bar). */
    public String get(String key) {
        String env = "RENAMI_" + key.toUpperCase().replace('.', '_');
        String value = System.getenv(env);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = props.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static Config load() {
        return new Config();
    }

    /** Persist a key into ~/.renami/config.properties (read-modify-write). */
    public static synchronized void set(String key, String value) {
        Path file = dataDir().resolve("config.properties");
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                System.err.println("warning: cannot read " + file + ": " + e.getMessage());
            }
        }
        p.setProperty(key, value);
        try {
            Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) {
                p.store(out, "renami");
            }
        } catch (IOException e) {
            System.err.println("warning: cannot write " + file + ": " + e.getMessage());
        }
    }

    /** Remove a persisted key (the API-key panel uses this when a field is cleared). */
    public static synchronized void unset(String key) {
        Path file = dataDir().resolve("config.properties");
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            System.err.println("warning: cannot read " + file + ": " + e.getMessage());
            return;
        }
        if (p.remove(key) == null) {
            return;
        }
        try (var out = Files.newOutputStream(file)) {
            p.store(out, "renami");
        } catch (IOException e) {
            System.err.println("warning: cannot write " + file + ": " + e.getMessage());
        }
    }
}
