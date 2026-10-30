package app.renami.web.model;

/** A single episode of a season. */
public record EpisodeInfo(int season, int episode, String name, String airDate) {
}
