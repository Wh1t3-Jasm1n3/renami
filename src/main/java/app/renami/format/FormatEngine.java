package app.renami.format;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders rename templates of the form {@code "{n} - {s00e00} - {t}"}.
 * Each {@code {expression}} is evaluated as a Groovy expression against a variable binding,
 * which gives users the full power of the Groovy language inside format strings.
 */
public final class FormatEngine {

    private static final Pattern EXPR = Pattern.compile("\\{([^{}]+)\\}");

    private FormatEngine() {
    }

    /**
     * @param template e.g. {@code "{n} - {s00e00} - {t}"}
     * @param vars     binding variables, e.g. n="Breaking Bad", s00e00="S01E02", t="Cat in the Hat"
     */
    public static String format(String template, Map<String, Object> vars) {
        Binding binding = new NullSafeBinding(vars);
        Matcher m = EXPR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String expression = m.group(1);
            Object value;
            try {
                value = new GroovyShell(binding).evaluate(expression);
            } catch (groovy.lang.MissingPropertyException e) {
                value = null;
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid format expression: {" + expression + "} — " + e.getMessage(), e);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Unknown variables resolve to null (rendered as empty) instead of throwing. */
    private static final class NullSafeBinding extends Binding {
        NullSafeBinding(Map<String, Object> vars) {
            super(new LinkedHashMap<>(vars));
        }

        @Override
        public Object getVariable(String name) {
            return hasVariable(name) ? super.getVariable(name) : null;
        }
    }

    /** Convenience: build the standard variable map used by the rename pipeline. */
    public static Map<String, Object> standardVars(String name, Integer year, String episodeFormat, String episodeTitle, Integer season, Integer episode) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("n", name);                                   // new name (show / movie)
        vars.put("y", year == null || year == 0 ? "" : year);  // year
        vars.put("t", episodeTitle == null ? "" : episodeTitle); // episode title
        vars.put("s00e00", episodeFormat == null ? "" : episodeFormat);
        vars.put("fn", name);                                 // alias
        if (season != null) {
            vars.put("s", season);
            vars.put("sn", String.format("S%02d", season));
        }
        if (episode != null) {
            vars.put("e", episode);
            vars.put("en", String.format("E%02d", episode));
        }
        return vars;
    }
}
