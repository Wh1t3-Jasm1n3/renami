package app.renami.web;

import app.renami.util.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasourcesTest {

    @Test
    void kitsuNeedsNoApiKey() throws Exception {
        assertEquals("Kitsu", Datasources.create(Config.load(), "kitsu", true).name());
    }

    @Test
    void unknownDatasourceNamesTheValidOnes() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> Datasources.create(Config.load(), "animedb", true));
        assertTrue(error.getMessage().contains("kitsu"));
    }
}
