package app.renami.format;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatEngineTest {

    @Test
    void rendersStandardVariables() {
        Map<String, Object> vars = FormatEngine.standardVars(
                "Breaking Bad", 2008, "S01E02", "Cat in the Hat", 1, 2);
        assertEquals("Breaking Bad - S01E02 - Cat in the Hat",
                FormatEngine.format("{n} - {s00e00} - {t}", vars));
    }

    @Test
    void evaluatesGroovyExpressions() {
        Map<String, Object> vars = FormatEngine.standardVars("firefly", 2002, "S01E01", null, 1, 1);
        assertEquals("FIREFLY", FormatEngine.format("{n.toUpperCase()}", vars));
        assertEquals("01x01", FormatEngine.format("{sn.replace('S','')}x{en.replace('E','')}", vars));
    }

    @Test
    void unknownVariablesRenderEmpty() {
        assertEquals("[]", FormatEngine.format("[{nope}]", Map.of()));
    }

    @Test
    void conditionalGroovyExpression() {
        Map<String, Object> vars = FormatEngine.standardVars("Inception", 2010, null, null, null, null);
        assertEquals("Inception (2010)", FormatEngine.format("{n} ({y})", vars));
    }
}
