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

    // ---- regressions from the real movies library (Nov 2026 dry runs) ----

    @Test
    void titleEndsAtFirstQualityTokenNotAfterIt() {
        // group after a dot ("x264.grp") and after a space ("x264 GRP") must not leak
        assertEquals("Batman Beyond Return of the Joker",
                FileNameParser.parse("Batman Beyond Return of the Joker 1080p BRRip x264.decatora27.mkv", "mkv").titleGuess());
        assertEquals("Batman- Assault on Arkham",
                FileNameParser.parse("Batman- Assault on Arkham (2014) 1080p BluRay x264 TuT.mkv", "mkv").titleGuess());
        assertEquals("Your Name",
                FileNameParser.parse("Your Name 2016 2160p BluRay DUAL DTS-HD MA5 1 x265-Kitsune.mkv", "mkv").titleGuess());
        assertEquals("Whisper of the Heart",
                FileNameParser.parse("Whisper.of.the.Heart.1995.BluRay.1080p.x265.10bit.4Audio.MNHD-FRDS.mkv", "mkv").titleGuess());
    }

    @Test
    void bracketedSourceLineDoesNotLeaveTrailingDash() {
        assertEquals("Ant-Man",
                FileNameParser.parse("Ant-Man (2015) - [BluRay 2160p HEVC x265 10bit HDR10 TrueHD Atmos 7.1].mkv", "mkv").titleGuess());
        assertEquals("The Matrix Resurrections",
                FileNameParser.parse("The Matrix Resurrections (2021) - [BluRay 2160p HEVC].mkv", "mkv").titleGuess());
    }

    @Test
    void releaseFlagsEndTheTitle() {
        assertEquals("Basic Instinct",
                FileNameParser.parse("Basic Instinct 1992 Unrated Directors Cut 1080p BluRay x264-OFT.mkv", "mkv").titleGuess());
        assertEquals("Sliver",
                FileNameParser.parse("Sliver 1993 Unrated UHD BluRay 2160p DDP 5 1 DV HDR x265-hallowed.mkv", "mkv").titleGuess());
    }

    @Test
    void plainTitlesKeepTheirWords() {
        assertEquals("FIFA World Cup", FileNameParser.parse("FIFA World Cup (2026).mkv", "mkv").titleGuess());
        assertEquals("Star Wars Episode V - The Empire Strikes Back",
                FileNameParser.parse("Star Wars Episode V - The Empire Strikes Back (1980).mkv", "mkv").titleGuess());
        assertEquals("Now You See Me 2", FileNameParser.parse("Now You See Me 2 (2016).mkv", "mkv").titleGuess());
        assertEquals("Cars 3", FileNameParser.parse("Cars 3 2017 1080p BluRay x264-OFT.mkv", "mkv").titleGuess());
    }

    @Test
    void parenthesisedYearWinsOverTitleNumber() {
        ReleaseInfo info = FileNameParser.parse(
                "Blade Runner 2049 (2017) - [UHD BluRay HYBRID REMUX 2160p HEVC 10bit DV HDR10 TrueHD Atmos 7.1].mkv", "mkv");
        assertEquals("Blade Runner 2049", info.titleGuess());
        assertEquals(2017, info.year());
    }

    @Test
    void remuxInsideBracketsDoesNotLeaveDanglingBracket() {
        ReleaseInfo info = FileNameParser.parse("Interstellar - [REMUX 2160p HEVC 10bit].mkv", "mkv");
        assertEquals("Interstellar", info.titleGuess());
    }

    @Test
    void spacedCodecNumberIsNotALooseEpisode() {
        // "H 265" is the h265 codec spelled with a space; the loose 3-digit
        // heuristic must not turn it into S02E65 for a movie
        ReleaseInfo suzume = FileNameParser.parse("Suzume 2022 2160p BluRay DTS-HD MA 5 1 DV HDR H 265-SasukeducK.mkv", "mkv");
        assertEquals(false, suzume.hasEpisode());
        assertEquals("Suzume", suzume.titleGuess());
        assertEquals(2022, suzume.year());

        ReleaseInfo spider = FileNameParser.parse("Spider-Man Brand New Day 2026 2160p iT WEB-DL DDP5 1 Atmos DV HDR H 265-BYNDR.mkv", "mkv");
        assertEquals(false, spider.hasEpisode());
        assertEquals(2026, spider.year());
    }

    @Test
    void looseEpisodeStillWorksWhenItEndsTheName() {
        assertEquals(new EpisodeNumber(1, 2), FileNameParser.parse("The Office 102.avi", "avi").episode());
    }
}
