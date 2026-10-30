package app.renami.parse;

/** Season + episode number pair. */
public record EpisodeNumber(int season, int episode) {

    /** Formats as {@code S01E02}. */
    public String format() {
        return String.format("S%02dE%02d", season, episode);
    }
}
