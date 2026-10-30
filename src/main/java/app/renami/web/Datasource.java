package app.renami.web;

import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;

import java.io.IOException;
import java.util.List;

/**
 * Metadata provider abstraction. Datasource ids are opaque strings because
 * providers use different id spaces (TMDb numeric, TVDB numeric, OMDb "tt…").
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

    List<MovieInfo> searchMovie(String query, int year) throws IOException;

    /** True for offline datasources: the pipeline then renames from parsed filename facts only. */
    default boolean networkFree() {
        return false;
    }
}
