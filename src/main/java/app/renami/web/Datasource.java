package app.renami.web;

import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;

import java.io.IOException;
import java.util.List;

/** Metadata provider abstraction; TMDb today, TheTVDB/OMDb/AniDB in later phases. */
public interface Datasource {

    String name();

    List<ShowInfo> searchShow(String query, int year) throws IOException;

    List<EpisodeInfo> getSeason(int showId, int season) throws IOException;

    List<MovieInfo> searchMovie(String query, int year) throws IOException;
}
