package app.renami.gui;

import app.renami.web.model.EpisodeInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The guide-driven attach logic: which file lands on which episode row. */
class EpisodesAttachTest {

    private static List<EpisodeInfo> season1() {
        return List.of(
                new EpisodeInfo(1, 1, "Pilot", "1964-01-04"),
                new EpisodeInfo(1, 2, "Zwei Seiten", "1964-03-16"),
                new EpisodeInfo(1, 3, "Abschied", "1964-05-19"));
    }

    private static Path f(String name) {
        return Paths.get("/tv/" + name);
    }

    @Test
    void sxxeyyAttachesToThatRow() {
        Map<Integer, Path> map = EpisodesPanel.autoAttach(
                List.of(f("Show S01E02.mkv")), season1(), 1);
        assertEquals(Map.of(1, f("Show S01E02.mkv")), map);
    }

    @Test
    void bareTrailingNumberAttachesToThatEpisode() {
        Map<Integer, Path> map = EpisodesPanel.autoAttach(
                List.of(f("Die Drehscheibe 3.mkv")), season1(), 1);
        assertEquals(Map.of(2, f("Die Drehscheibe 3.mkv")), map);
    }

    @Test
    void dashAttachedAbsoluteNumberAttaches() {
        Map<Integer, Path> map = EpisodesPanel.autoAttach(
                List.of(f("[GRP] Show - 02.mkv")), season1(), 1);
        assertEquals(Map.of(1, f("[GRP] Show - 02.mkv")), map);
    }

    @Test
    void airDateInNameWinsOverMisreadAbsoluteNumber() {
        // The filename parser reads the trailing "-16" of the date as absolute #16;
        // the guide's air date must win instead.
        Map<Integer, Path> map = EpisodesPanel.autoAttach(
                List.of(f("die drehscheibe 1964-03-16.mkv")), season1(), 1);
        assertEquals(Map.of(1, f("die drehscheibe 1964-03-16.mkv")), map);
    }

    @Test
    void dottedDateAlsoMatchesAirDate() {
        Map<Integer, Path> map = EpisodesPanel.autoAttach(
                List.of(f("show 1964.05.19.mkv")), season1(), 1);
        assertEquals(Map.of(2, f("show 1964.05.19.mkv")), map);
    }

    @Test
    void wrongSeasonDoesNotAttachByNumber() {
        // S02E03 while viewing season 1: no number match, falls to order fill.
        Map<Integer, Path> map = EpisodesPanel.autoAttach(
                List.of(f("Show S02E03.mkv")), season1(), 1);
        assertEquals(Map.of(0, f("Show S02E03.mkv")), map);
    }

    @Test
    void looseThreeDigitNumberAttaches() {
        Map<Integer, Path> map = EpisodesPanel.autoAttach(
                List.of(f("Show 103.mkv")), season1(), 1);
        assertEquals(Map.of(2, f("Show 103.mkv")), map);
    }

    @Test
    void unmatchedFilesFillRemainingRowsInNameOrder() {
        Map<Integer, Path> map = EpisodesPanel.autoAttach(
                List.of(f("tape b.mkv"), f("tape a.mkv")), season1(), 1);
        assertEquals(Map.of(0, f("tape a.mkv"), 1, f("tape b.mkv")), map);
    }

    @Test
    void twoFilesClaimingSameEpisodeSecondFallsToOrder() {
        Map<Integer, Path> map = EpisodesPanel.autoAttach(
                List.of(f("Show S01E02.mkv"), f("Show S01E02 copy.mkv")), season1(), 1);
        assertEquals(2, map.size());
        assertEquals(f("Show S01E02.mkv"), map.get(1));
        assertEquals(f("Show S01E02 copy.mkv"), map.get(0));
    }

    @Test
    void yearIsNotAnEpisodeNumber() {
        // "1964" must not attach to episode 64 (or any row by number).
        Map<Integer, Path> map = EpisodesPanel.autoAttach(
                List.of(f("Die Drehscheibe 1964.mkv")), season1(), 1);
        assertEquals(Map.of(0, f("Die Drehscheibe 1964.mkv")), map);
    }
}
