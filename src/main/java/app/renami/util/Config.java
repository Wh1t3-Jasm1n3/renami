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
}
