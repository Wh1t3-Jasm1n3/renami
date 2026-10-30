package app.renami.similarity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameScorerTest {

    @Test
    void identicalStringsScoreOne() {
        assertEquals(1.0, NameScorer.scoreRaw("Breaking Bad", "breaking bad"));
    }

    @Test
    void similarTitlesScoreHigh() {
        assertTrue(NameScorer.scoreRaw("Breaking Bad", "Breaking.Bad.2008") > 0.7);
    }

    @Test
    void differentTitlesScoreLow() {
        assertTrue(NameScorer.scoreRaw("The Office", "Breaking Bad") < 0.4);
    }

    @Test
    void accentsAndPunctuationAreNormalized() {
        assertEquals(1.0, NameScorer.scoreRaw("Café de Flore", "cafe-de-flore"));
    }
}
