package app.renami.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileNameParserTest {

    @Test
    void parsesStandardSceneName() {
        ReleaseInfo info = FileNameParser.parse("Breaking.Bad.S01E02.1080p.NF.WEBDL.x265.mkv", "mkv");
        assertEquals("Breaking Bad", info.titleGuess());
        assertEquals(new EpisodeNumber(1, 2), info.episode());
    }

    @Test
    void parsesNxNPattern() {
        ReleaseInfo info = FileNameParser.parse("the.walking.dead.3x07.720p.mp4", "mp4");
        assertEquals("the walking dead", info.titleGuess());
        assertEquals(new EpisodeNumber(3, 7), info.episode());
    }

    @Test
    void parsesLooseThreeDigit() {
        ReleaseInfo info = FileNameParser.parse("The Office 102.avi", "avi");
        assertEquals(new EpisodeNumber(1, 2), info.episode());
    }

    @Test
    void parsesMovieWithYear() {
        ReleaseInfo info = FileNameParser.parse("Inception.2010.1080p.BluRay.x264.mkv", "mkv");
        assertEquals("Inception", info.titleGuess());
        assertEquals(2010, info.year());
        assertEquals(false, info.hasEpisode());
    }

    @Test
    void yearIsNotMistakenForEpisode() {
        ReleaseInfo info = FileNameParser.parse("Blade Runner 1982 Remastered.mkv", "mkv");
        assertEquals(false, info.hasEpisode());
        assertEquals(1982, info.year());
    }

    @Test
    void parsesAnimeAbsoluteEpisode() {
        ReleaseInfo info = FileNameParser.parse("[SubsPlease] Steins;Gate - 05 (1080p) [10bit].mkv", "mkv");
        assertEquals("Steins;Gate", info.titleGuess());
        assertEquals(5, info.absoluteEpisode());
        assertEquals(false, info.hasEpisode());
    }

    @Test
    void dashedThreeDigitPrefersAbsoluteOverLooseSeasonGuess() {
        ReleaseInfo info = FileNameParser.parse("Attack on Titan - 104.mkv", "mkv");
        assertEquals(null, info.episode());
        assertEquals(104, info.absoluteEpisode());
        assertEquals("Attack on Titan", info.titleGuess());
    }

    @Test
    void movieSequelIsNotAnEpisode() {
        ReleaseInfo info = FileNameParser.parse("The.Godfather.2.1974.BluRay.mkv", "mkv");
        assertEquals(false, info.hasEpisode());
        assertEquals(null, info.absoluteEpisode());
        assertEquals("The Godfather 2", info.titleGuess());
    }
}
