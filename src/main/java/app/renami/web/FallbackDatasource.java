package app.renami.web;

import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tries datasources in preference order and answers with the first one that
 * actually knows something. This is what makes {@code --db auto} work with no
 * API key at all: a keyed provider answers when the user configured one, and
 * the key-free providers (TVmaze, Kitsu) answer when nobody did.
 * <p>
 * Provider id spaces collide (TMDb and TVmaze are both plain integers), so ids
 * handed back to callers are tagged with the answering provider and
 * {@link #getSeason} / {@link #mapAbsoluteEpisode} route on that tag. The tag
 * never reaches a file name: ids are only ever fed back into this interface.
 */
public final class FallbackDatasource implements Datasource {

    private static final char TAG_SEPARATOR = ':';

    private final List<Datasource> chain;

    public FallbackDatasource(List<Datasource> chain) {
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("no datasources in chain");
        }
        this.chain = List.copyOf(chain);
    }

    @Override
    public String name() {
        return chain.size() == 1 ? chain.get(0).name() : "auto(" + chain.size() + ")";
    }

    /** Providers this chain consults, in order. */
    public List<String> providers() {
        return chain.stream().map(Datasource::name).toList();
    }

    @Override
    public List<ShowInfo> searchShow(String query, int year) throws IOException {
        IOException firstFailure = null;
        for (Datasource datasource : chain) {
            try {
                List<ShowInfo> hits = datasource.searchShow(query, year);
                if (!hits.isEmpty()) {
                    return hits.stream()
                            .map(s -> new ShowInfo(tag(datasource, s.id()), s.name(), s.firstAirDate()))
                            .toList();
                }
            } catch (IOException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure; // nothing answered anywhere: surface the real problem
        }
        return List.of();
    }

    @Override
    public List<MovieInfo> searchMovie(String query, int year) throws IOException {
        IOException firstFailure = null;
        for (Datasource datasource : chain) {
            if (datasource.animeOnly()) {
                continue; // anime databases answer with false matches for live-action movies
            }
            try {
                List<MovieInfo> hits = datasource.searchMovie(query, year);
                if (!hits.isEmpty()) {
                    return hits.stream()
                            .map(m -> new MovieInfo(tag(datasource, m.id()), m.title(), m.releaseDate()))
                            .toList();
                }
            } catch (IOException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        return List.of();
    }

    @Override
    public List<EpisodeInfo> getSeason(String showId, int season) throws IOException {
        Routing routing = route(showId);
        IOException firstFailure = null;
        for (Datasource datasource : routing.candidates()) {
            try {
                List<EpisodeInfo> episodes = datasource.getSeason(routing.id(), season);
                if (!episodes.isEmpty()) {
                    return episodes;
                }
            } catch (IOException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        return List.of();
    }

    @Override
    public List<EpisodeInfo> getSeason(String showId, int season, String order) throws IOException {
        Routing routing = route(showId);
        IOException firstFailure = null;
        for (Datasource datasource : routing.candidates()) {
            try {
                List<EpisodeInfo> episodes = datasource.getSeason(routing.id(), season, order);
                if (!episodes.isEmpty()) {
                    return episodes;
                }
            } catch (IOException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        return List.of();
    }

    @Override
    public List<EpisodeInfo> getSeason(String showId, int season, String order, String language) throws IOException {
        Routing routing = route(showId);
        IOException firstFailure = null;
        for (Datasource datasource : routing.candidates()) {
            try {
                List<EpisodeInfo> episodes = datasource.getSeason(routing.id(), season, order, language);
                if (!episodes.isEmpty()) {
                    return episodes;
                }
            } catch (IOException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        return List.of();
    }

    @Override
    public EpisodeInfo mapAbsoluteEpisode(String showId, int absolute) throws IOException {
        Routing routing = route(showId);
        for (Datasource datasource : routing.candidates()) {
            EpisodeInfo mapped = datasource.mapAbsoluteEpisode(routing.id(), absolute);
            if (mapped != null) {
                return mapped;
            }
        }
        return null;
    }

    @Override
    public boolean networkFree() {
        return chain.stream().allMatch(Datasource::networkFree);
    }

    private static String tag(Datasource datasource, String id) {
        return datasource.name().toLowerCase(java.util.Locale.ROOT) + TAG_SEPARATOR + id;
    }

    /**
     * Splits a tagged id back into the provider that issued it plus the raw id.
     * Untagged ids (a caller talking to one provider directly) route to the whole chain.
     */
    private Routing route(String showId) {
        int separator = showId.indexOf(TAG_SEPARATOR);
        if (separator < 0) {
            return new Routing(showId, chain);
        }
        String provider = showId.substring(0, separator);
        String raw = showId.substring(separator + 1);
        List<Datasource> matching = new ArrayList<>();
        for (Datasource datasource : chain) {
            if (datasource.name().toLowerCase(java.util.Locale.ROOT).equals(provider)) {
                matching.add(datasource);
            }
        }
        return new Routing(raw, matching.isEmpty() ? chain : matching);
    }

    private record Routing(String id, List<Datasource> candidates) {
    }
}
