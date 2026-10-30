package app.renami.parse;

/** Facts parsed from a single release filename. */
public record ReleaseInfo(
        String fileName,
        String titleGuess,
        EpisodeNumber episode,   // null for movies / unknown
        int year                 // 0 when absent
) {
    public boolean hasEpisode() {
        return episode != null;
    }
}
