package app.renami.parse;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured facts (show/movie name guess, season, episode, year) from a release filename.
 * Original implementation: delimiter normalization + pattern scanning over the raw name.
 */
public final class FileNameParser {

    private static final Pattern EPISODE_PATTERN = Pattern.compile(
            "(?i)\\bs(\\d{1,2})[ ._-]?e(?:p)?(\\d{1,3})\\b");

    private static final Pattern NxN_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,2})x(\\d{2,3})\\b");

    private static final Pattern THREEDIGIT_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d)(\\d{2})(?!\\d)");

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");

    /** Tokens that describe encoding/source, never part of the title. */
    private static final Pattern QUALITY_PATTERN = Pattern.compile(
            "(?i)\\b(?:720p|1080p|2160p|4k|uhd|hd|sd|x264|x265|h264|h265|hevc|av1|"
                    + "hdr10\\+?|hdr|10bit|8bit|web-?dl|webrip|web|bluray|bdrip|brrip|hdtv|dvdrip|"
                    + "aac|ac3|eac3|dts(?:-hd)?|dd5\\.1|5\\.1|7\\.1|atmos|multi|proper|repack|"
                    + "internal|limited|extended|unrated|imax)\\b");

    private FileNameParser() {
    }

    /** Replaces dot/underscore runs with spaces and strips the file extension. */
    public static String normalizeDelimiters(String fileName, String extension) {
        String name = fileName;
        if (extension != null && !extension.isEmpty() && name.toLowerCase(Locale.ROOT).endsWith("." + extension.toLowerCase(Locale.ROOT))) {
            name = name.substring(0, name.length() - extension.length() - 1);
        }
        return name.replaceAll("[._]+", " ").replaceAll("\\s{2,}", " ").trim();
    }

    public static Optional<Integer> parseYear(String text) {
        Matcher m = YEAR_PATTERN.matcher(text);
        if (m.find()) {
            return Optional.of(Integer.parseInt(m.group()));
        }
        return Optional.empty();
    }

    public static Optional<EpisodeNumber> parseEpisode(String text) {
        Matcher m = EPISODE_PATTERN.matcher(text);
        if (m.find()) {
            return Optional.of(new EpisodeNumber(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        }
        m = NxN_PATTERN.matcher(text);
        if (m.find()) {
            return Optional.of(new EpisodeNumber(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        }
        return Optional.empty();
    }

    /**
     * Bare 3-digit pattern (e.g. {@code "The Office 102.mp4"} → S01E02).
     * Only applied when no stronger pattern matched and the digits are not the year.
     */
    public static Optional<EpisodeNumber> parseLooseEpisode(String text, int year) {
        Matcher m = THREEDIGIT_PATTERN.matcher(text);
        while (m.find()) {
            int season = Integer.parseInt(m.group(1));
            int episode = Integer.parseInt(m.group(2));
            int asNumber = Integer.parseInt(m.group(0));
            if (asNumber == year || episode == 0 || season == 0 || episode > 99) {
                continue;
            }
            return Optional.of(new EpisodeNumber(season, episode));
        }
        return Optional.empty();
    }

    public static String stripQualityTokens(String text) {
        return QUALITY_PATTERN.matcher(text).replaceAll(" ").replaceAll("\\s{2,}", " ").trim();
    }

    /**
     * Best guess for the series/movie title: text before the episode marker (episodes)
     * or the whole name minus year/quality noise (movies).
     */
    public static String guessTitle(String normalized, int year) {
        String title = normalized;

        Matcher m = EPISODE_PATTERN.matcher(title);
        if (m.find()) {
            title = title.substring(0, m.start());
        } else {
            m = NxN_PATTERN.matcher(title);
            if (m.find()) {
                title = title.substring(0, m.start());
            }
        }

        title = stripQualityTokens(title);
        if (year > 0) {
            title = title.replaceAll("\\b" + year + "\\b", " ");
        }
        // release group tags like "[GRP]" or trailing "- GRP"
        title = title.replaceAll("[\\[\\(][^\\]\\)]*[\\]\\)]", " ");
        title = title.replaceAll("[-–—]\\s*\\S{3,12}$", " ");
        return title.replaceAll("\\s{2,}", " ").trim();
    }

    public static ReleaseInfo parse(String fileName, String extension) {
        String normalized = normalizeDelimiters(fileName, extension);
        int year = parseYear(normalized).orElse(0);
        // quality tokens (x264, 1080p, …) must not be mistaken for loose episode numbers
        EpisodeNumber episode = parseEpisode(normalized)
                .or(() -> parseLooseEpisode(stripQualityTokens(normalized), year))
                .orElse(null);
        String title = guessTitle(normalized, year);
        return new ReleaseInfo(fileName, title, episode, year);
    }
}
