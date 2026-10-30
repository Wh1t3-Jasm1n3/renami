package app.renami.web.local;

import app.renami.web.Datasource;
import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;

import java.util.List;

/**
 * Offline datasource: no network lookups. The rename pipeline uses the facts
 * parsed from the filename itself (title guess, SxxEyy, year).
 */
public final class LocalDatasource implements Datasource {

    @Override
    public String name() {
        return "Local";
    }

    @Override
    public List<ShowInfo> searchShow(String query, int year) {
        return List.of();
    }

    @Override
    public List<EpisodeInfo> getSeason(String showId, int season) {
        return List.of();
    }

    @Override
    public List<MovieInfo> searchMovie(String query, int year) {
        return List.of();
    }
}
