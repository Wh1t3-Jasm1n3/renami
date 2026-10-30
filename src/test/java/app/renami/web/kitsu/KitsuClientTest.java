package app.renami.web.kitsu;

import app.renami.web.model.EpisodeInfo;
import app.renami.web.model.MovieInfo;
import app.renami.web.model.ShowInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kitsu JSON:API parsing against recorded payloads — no network in tests. */
class KitsuClientTest {

    /** Serves fixtures by URL pattern and records every request. */
    private static final class Stub implements app.renami.web.JsonGet {
        private final Map<String, String> byUrl;
        private final List<String> requested = new ArrayList<>();

        Stub(Map<String, String> byUrl) {
            this.byUrl = byUrl;
        }

        @Override
        public JsonObject get(String url, Map<String, String> headers) throws IOException {
            requested.add(url);
            for (Map.Entry<String, String> entry : byUrl.entrySet()) {
                if (url.contains(entry.getKey())) {
                    return JsonParser.parseString(entry.getValue()).getAsJsonObject();
                }
            }
            throw new IOException("unexpected request: " + url);
        }
    }

    private static final String SEARCH = """
            {"data":[
              {"id":"7442","type":"anime","attributes":{
                "canonicalTitle":"Attack on Titan","startDate":"2013-04-07","subtype":"TV",
                "titles":{"en":"Attack on Titan","en_jp":"Shingeki no Kyojin","en_us":"Attack on Titan",
                          "ja_jp":"進撃の巨人"},
                "abbreviatedTitles":["AoT","  ","Attack on Titan"]}},
              {"id":"9999","type":"anime","attributes":{"canonicalTitle":null,"titles":null}}
            ]}""";

    private static final String MOVIE_SEARCH = """
            {"data":[
              {"id":"176","type":"anime","attributes":{
                "canonicalTitle":"Spirited Away","startDate":"2001-07-20","subtype":"movie",
                "titles":{"en_jp":"Sen to Chihiro no Kamikakushi"}}}
            ]}""";

    private static final String EPISODES_PAGE_1 = """
            {"data":[
              {"id":"229115","attributes":{"seasonNumber":1,"number":1,"numberAbsolute":null,
                "canonicalTitle":"Asteroid Blues","airdate":"1998-10-23"}},
              {"id":"229116","attributes":{"seasonNumber":1,"number":2,"canonicalTitle":"TBush"}}
            ],"links":{"next":"https://kitsu.io/api/edge/anime/1/episodes?page%5Blimit%5D=20&page%5Boffset%5D=2"}}""";

    private static final String EPISODES_PAGE_2 = """
            {"data":[
              {"id":"229117","attributes":{"seasonNumber":1,"number":3,"canonicalTitle":"Wild Horses"}},
              {"id":"229118","attributes":{"number":null,"canonicalTitle":"Unnumbered special"}}
            ],"links":{"next":null}}""";

    @Test
    void searchShowOffersEveryKnownTitleAsACandidate() throws Exception {
        Stub http = new Stub(Map.of("/anime?" + "filter%5Btext%5D=attack", SEARCH));
        List<ShowInfo> results = new KitsuClient(http).searchShow("attack on titan", 0);

        assertEquals(List.of("Attack on Titan", "Shingeki no Kyojin", "進撃の巨人", "AoT"),
                results.stream().map(ShowInfo::name).toList());
        assertTrue(results.stream().allMatch(s -> s.id().equals("7442")));
        assertEquals(2013, results.get(0).year());
        assertEquals(1, http.requested.size());
        assertTrue(http.requested.get(0).contains("page%5Blimit%5D=20"));
    }

    @Test
    void searchMovieUsesTheMovieSubtypeFilter() throws Exception {
        Stub http = new Stub(Map.of("/anime?", MOVIE_SEARCH));
        List<MovieInfo> movies = new KitsuClient(http).searchMovie("spirited away", 2001);

        assertEquals(List.of("Spirited Away", "Sen to Chihiro no Kamikakushi"),
                movies.stream().map(MovieInfo::title).toList());
        assertEquals(2001, movies.get(0).year());
        assertTrue(http.requested.get(0).contains("filter%5Bsubtype%5D=movie"));
    }

    @Test
    void episodeListsFollowOffsetPagination() throws Exception {
        Stub http = new Stub(Map.of(
                "page%5Boffset%5D=0", EPISODES_PAGE_1,
                "page%5Boffset%5D=2", EPISODES_PAGE_2));
        List<EpisodeInfo> episodes = new KitsuClient(http).getSeason("1", 1);

        assertEquals(List.of("Asteroid Blues", "TBush", "Wild Horses"),
                episodes.stream().map(EpisodeInfo::name).toList());
        assertEquals(List.of(1, 2, 3), episodes.stream().map(EpisodeInfo::episode).toList());
        assertEquals(2, http.requested.size()); // stops when links.next is null
    }

    @Test
    void otherSeasonsAreFilteredFromTheRecord() throws Exception {
        String mixed = """
                {"data":[
                  {"id":"1","attributes":{"seasonNumber":1,"number":1,"canonicalTitle":"S1E1"}},
                  {"id":"2","attributes":{"seasonNumber":2,"number":1,"canonicalTitle":"S2E1"}},
                  {"id":"3","attributes":{"seasonNumber":2,"number":2,"canonicalTitle":"S2E2"}}
                ],"links":{}}""";
        KitsuClient client = new KitsuClient(new Stub(Map.of("page%5Boffset%5D=0", mixed)));

        assertEquals(List.of("S2E1", "S2E2"),
                client.getSeason("1", 2).stream().map(EpisodeInfo::name).toList());
        // a record without the requested season is answered with what it has, not with nothing
        assertEquals(3, client.getSeason("1", 7).size());
    }

    @Test
    void absoluteEpisodeIsResolvedByDirectNumberFirst() throws Exception {
        String direct = """
                {"data":[{"id":"5","attributes":{"seasonNumber":1,"number":5,
                  "canonicalTitle":"My Hero Practice","airdate":"2016-08-06"}}],"links":{}}""";
        Stub http = new Stub(Map.of("filter%5Bnumber%5D=5", direct));

        EpisodeInfo mapped = new KitsuClient(http).mapAbsoluteEpisode("1", 5);
        assertEquals("My Hero Practice", mapped.name());
        assertEquals(1, mapped.season());
        assertEquals(5, mapped.episode());
        assertEquals(1, http.requested.size()); // no full-record scan needed
    }

    @Test
    void absoluteEpisodeFallsBackToScanningNumberAbsolute() throws Exception {
        String scan = """
                {"data":[
                  {"id":"6","attributes":{"seasonNumber":2,"number":3,"numberAbsolute":55,
                    "canonicalTitle":"The One"}}
                ],"links":null}""";
        Stub http = new Stub(Map.of(
                "filter%5Bnumber%5D=55", "{\"data\":[],\"links\":{}}",
                "page%5Boffset%5D=0", scan));

        EpisodeInfo mapped = new KitsuClient(http).mapAbsoluteEpisode("1", 55);
        assertEquals(new EpisodeInfo(2, 3, "The One", null), mapped);
    }

    @Test
    void unknownAbsoluteEpisodeYieldsNull() throws Exception {
        Stub http = new Stub(Map.of(
                "filter%5Bnumber%5D=900", "{\"data\":[],\"links\":{}}",
                "page%5Boffset%5D=0", EPISODES_PAGE_1,
                "page%5Boffset%5D=2", EPISODES_PAGE_2));
        assertNull(new KitsuClient(http).mapAbsoluteEpisode("1", 900));
    }

    @Test
    void absoluteOrderSortsByNumberAbsolute() throws Exception {
        String fixture = """
                {"data":[
                  {"id":"1","attributes":{"seasonNumber":1,"number":1,"numberAbsolute":101,"canonicalTitle":"Late"}},
                  {"id":"2","attributes":{"seasonNumber":1,"number":2,"numberAbsolute":1,"canonicalTitle":"First"}}
                ],"links":{}}""";
        List<EpisodeInfo> ordered = new KitsuClient(new Stub(Map.of("page%5Boffset%5D=0", fixture)))
                .getSeason("1", 1, "absolute");
        assertEquals(List.of("First", "Late"), ordered.stream().map(EpisodeInfo::name).toList());
    }
}
