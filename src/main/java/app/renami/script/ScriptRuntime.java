package app.renami.script;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Groovy script host, mirroring the scripting contract of mature media tools:
 * scripts see {@code fn} (current file), {@code info} (parsed release info),
 * {@code source} (input root), {@code target} (output root) and user {@code --def} values.
 */
public final class ScriptRuntime {

    private final Map<String, String> definitions;
    private final GroovyShell shell = new GroovyShell();

    public ScriptRuntime(Map<String, String> definitions) {
        this.definitions = new LinkedHashMap<>(definitions);
    }

    /** Parse repeatable {@code --def key=value} options; numeric/boolean values are type-coerced. */
    public static Map<String, String> parseDefs(List<String> raw) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String entry : raw) {
            int split = entry.indexOf('=');
            if (split > 0) {
                map.put(entry.substring(0, split), entry.substring(split + 1));
            }
        }
        return map;
    }

    /** Coerce string definitions so Groovy comparisons (year >= minYear) work naturally. */
    private static Object coerce(String value) {
        if (value.equals("true") || value.equals("false")) {
            return Boolean.valueOf(value);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ignored) {
        }
        return value;
    }

    /** Compile once, run per file with a fresh binding. */
    public Object run(Path scriptFile, Map<String, Object> binding) throws IOException {
        Script script = shell.parse(scriptFile.toFile());
        return invoke(script.getClass(), binding);
    }

    /** Compile an inline boolean expression once; evaluate per file with a fresh binding. */
    public Predicate<Map<String, Object>> compilePredicate(String expression) {
        Class<? extends Script> clazz = shell.parse(expression).getClass();
        return binding -> org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation
                .castToBoolean(invoke(clazz, binding));
    }

    private Object invoke(Class<? extends Script> scriptClass, Map<String, Object> binding) {
        Binding b = new Binding();
        definitions.forEach((k, v) -> b.setVariable(k, coerce(v)));
        binding.forEach(b::setVariable);
        b.setVariable("renami", new ScriptFunctions());
        return InvokerHelper.createScript(scriptClass, b).run();
    }

    /** Convenience API exposed to scripts as the {@code renami} variable. */
    public static final class ScriptFunctions {

        /** Rename fn to a new name inside its current folder. Returns the new path. */
        public Path rename(java.io.File fn, String newName) throws IOException {
            Path source = fn.toPath();
            Path target = source.resolveSibling(newName);
            Files.move(source, target);
            return target;
        }

        /** Move fn into a destination folder keeping its name. Returns the new path. */
        public Path move(java.io.File fn, java.io.File destDir) throws IOException {
            Path source = fn.toPath();
            Path target = destDir.toPath().resolve(source.getFileName());
            Files.createDirectories(target.getParent());
            Files.move(source, target);
            return target;
        }

        public boolean isArchive(java.io.File fn) {
            return app.renami.archive.ArchiveTool.isArchive(fn.toPath());
        }
    }
}
