package app.renami.parse;

/** Facts parsed from a single release filename. */
public record ReleaseInfo(
        String fileName,
        String titleGuess,
        EpisodeNumber episode,   // null for movies / unknown
        int year,                // 0 when absent
        Integer absoluteEpisode  // anime-style trailing number (only when no SxxEyy matched)
) {
    public ReleaseInfo(String fileName, String titleGuess, EpisodeNumber episode, int year) {
        this(fileName, titleGuess, episode, year, null);
    }

    public boolean hasEpisode() {
        return episode != null;
    }

    public boolean hasAbsoluteEpisode() {
        return absoluteEpisode != null;
    }
}
