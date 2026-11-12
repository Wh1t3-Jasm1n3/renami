package app.renami.web;

import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;

import java.io.IOException;
import java.util.List;

/**
 * Metadata provider abstraction. Datasource ids are opaque strings because
 * providers use different id spaces (TMDb numeric, OMDb "tt…").
 */
public interface Datasource {

    String name();

    List<ShowInfo> searchShow(String query, int year) throws IOException;

    List<EpisodeInfo> getSeason(String showId, int season) throws IOException;

    /**
     * Season episodes in a specific ordering. Orders: default | aired | dvd.
     * Matters for anime and shows where broadcast and production orders differ.
     */
    default List<EpisodeInfo> getSeason(String showId, int season, String order) throws IOException {
        return getSeason(showId, season);
    }

    /**
     * Season episodes in a specific ordering and series language.
     * Providers without localized episode names ignore the language.
     */
    default List<EpisodeInfo> getSeason(String showId, int season, String order, String language) throws IOException {
        return getSeason(showId, season, order);
    }

    /**
     * Resolve an absolute (anime-style) episode number to a concrete season/episode.
     * Datasources with their own absolute order answer from their episode list;
     * providers without one can fall back to community episode mappings.
     */
    default EpisodeInfo mapAbsoluteEpisode(String showId, int absolute) throws IOException {
        for (EpisodeInfo info : getSeason(showId, 1, "absolute")) {
            if (info.episode() == absolute) {
                return info;
            }
        }
        return null;
    }

    List<MovieInfo> searchMovie(String query, int year) throws IOException;

    /** True for offline datasources: the pipeline then renames from parsed filename facts only. */
    default boolean networkFree() {
        return false;
    }

    /**
     * True for anime-only providers (Kitsu). The auto chain then leaves them out
     * of movie searches, so a live-action movie never gets a false anime match —
     * Anime databases (AniDB) stay out of the movie search chain, too.
     */
    default boolean animeOnly() {
        return false;
    }
}
