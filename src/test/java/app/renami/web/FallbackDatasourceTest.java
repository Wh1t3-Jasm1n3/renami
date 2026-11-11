package app.renami.web;

import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The provider chain behind {@code --db auto}: first answer wins, ids route back. */
class FallbackDatasourceTest {

    /** Minimal programmable provider: fixed answers, records season lookups. */
    private static class Fake implements Datasource {
        private final String name;
        private final List<ShowInfo> shows;
        private final List<MovieInfo> movies;
        private final IOException failure;
        final List<String> seasonCalls = new java.util.ArrayList<>();

        Fake(String name, List<ShowInfo> shows, List<MovieInfo> movies, IOException failure) {
            this.name = name;
            this.shows = shows;
            this.movies = movies;
            this.failure = failure;
        }

        static Fake shows(String name, ShowInfo... hits) {
            return new Fake(name, List.of(hits), List.of(), null);
        }

        static Fake silent(String name) {
            return new Fake(name, List.of(), List.of(), null);
        }

        static Fake failing(String name, String message) {
            return new Fake(name, List.of(), List.of(), new IOException(message));
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<ShowInfo> searchShow(String query, int year) throws IOException {
            if (failure != null) {
                throw failure;
            }
            return shows;
        }

        @Override
        public List<EpisodeInfo> getSeason(String showId, int season) throws IOException {
            seasonCalls.add(name + "/" + showId + "/" + season);
            if (failure != null) {
                throw failure;
            }
            return shows.isEmpty() ? List.of() : List.of(new EpisodeInfo(season, 1, "Pilot", null));
        }

        @Override
        public List<MovieInfo> searchMovie(String query, int year) throws IOException {
            if (failure != null) {
                throw failure;
            }
            return movies;
        }
    }

    @Test
    void firstNonEmptyProviderAnswersAndTagsItsIds() throws Exception {
        FallbackDatasource chain = new FallbackDatasource(List.of(
                Fake.silent("TMDb"),
                Fake.shows("TVmaze", new ShowInfo("169", "Breaking Bad", "2008-01-20"))));

        List<ShowInfo> hits = chain.searchShow("breaking bad", 0);
        assertEquals(1, hits.size());
        assertEquals("tvmaze:169", hits.get(0).id()); // tagged so season lookups route back
        assertEquals("Breaking Bad", hits.get(0).name());
    }

    @Test
    void taggedSeasonLookupsGoToTheirProviderOnly() throws Exception {
        Fake tmdb = Fake.shows("TMDb", new ShowInfo("1396", "Breaking Bad", "2008-01-20"));
        Fake tvmaze = Fake.shows("TVmaze", new ShowInfo("169", "Breaking Bad", "2008-01-20"));
        FallbackDatasource chain = new FallbackDatasource(List.of(tmdb, tvmaze));

        chain.getSeason("tvmaze:169", 3);
        assertEquals(List.of("TVmaze/169/3"), tvmaze.seasonCalls);
        assertTrue(tmdb.seasonCalls.isEmpty());

        chain.getSeason("1396", 1); // untagged: whole chain stays eligible
        assertEquals(List.of("TMDb/1396/1"), tmdb.seasonCalls);
    }

    @Test
    void allProvidersFailingSurfacesTheRealError() {
        FallbackDatasource chain = new FallbackDatasource(List.of(
                Fake.failing("TMDb", "invalid api key"),
                Fake.failing("TVmaze", "rate limited")));

        IOException error = assertThrows(IOException.class, () -> chain.searchShow("x", 0));
        assertEquals("invalid api key", error.getMessage());
    }

    @Test
    void everyoneAnsweringEmptyIsNotAnError() throws Exception {
        FallbackDatasource chain = new FallbackDatasource(List.of(
                Fake.silent("TMDb"), Fake.silent("TVmaze")));
        assertTrue(chain.searchShow("definitely nothing", 0).isEmpty());
        assertTrue(chain.searchMovie("definitely nothing", 0).isEmpty());
    }

    @Test
    void absoluteMappingRoutesOnTheTag() throws Exception {
        Fake kitsu = new Fake("Kitsu", List.of(new ShowInfo("7442", "Attack on Titan", null)),
                List.of(), null) {
            @Override
            public EpisodeInfo mapAbsoluteEpisode(String showId, int absolute) {
                return showId.equals("7442") ? new EpisodeInfo(1, absolute, "To You, 2000 Years Later", null) : null;
            }
        };
        FallbackDatasource chain = new FallbackDatasource(List.of(Fake.silent("TVmaze"), kitsu));

        EpisodeInfo mapped = chain.mapAbsoluteEpisode("kitsu:7442", 5);
        assertEquals(5, mapped.episode());
        assertNull(chain.mapAbsoluteEpisode("kitsu:9999", 5));
    }

    @Test
    void singleProviderChainKeepsItsOwnName() {
        FallbackDatasource chain = new FallbackDatasource(List.of(Fake.silent("TVmaze")));
        assertEquals("TVmaze", chain.name());
        assertEquals(List.of("TMDb", "TVmaze"),
                new FallbackDatasource(List.of(Fake.silent("TMDb"), Fake.silent("TVmaze"))).providers());
    }

    @Test
    void emptyChainIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FallbackDatasource(List.of()));
    }
}
