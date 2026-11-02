package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.SportsDbClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ActiveProfiles("test")
@RestClientTest(value = SportsDbClient.class, properties = {
        "everyone.sportsdb.base-url=https://sportsdb.test",
        "everyone.sportsdb.api-key=test-key"
})
@Import(SportsDbClientConfig.class)
@AutoConfigureMockRestServiceServer(enabled = false)
class SportsDbClientTest {

    @Autowired
    @Qualifier("sportsDbRestClient")
    private RestClient configuredRestClient;

    private SportsDbClient sportsDbClient;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        var builder = configuredRestClient.mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        sportsDbClient = new SportsDbClient(builder.build());
    }

    @Test
    void 리그와_시즌으로_경기_목록을_조회한다() {
        server.expect(requestTo(
                        "https://sportsdb.test/test-key/eventsseason.php"
                                + "?id=4328&s=2014-2015"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                ))
                .andRespond(withSuccess("""
                        {
                          "events": [
                            {
                              "idEvent": "441425",
                              "strEvent": "West Ham vs Tottenham",
                              "strSport": "Soccer",
                              "strLeague": "English Premier League",
                              "strSeason": "2014-2015",
                              "dateEvent": "2014-08-16",
                              "strThumb": "https://example.com/match.jpg",
                              "intHomeScore": "0"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = sportsDbClient.getEventsBySeason("4328", "2014-2015");

        assertThat(response).isNotNull();
        assertThat(response.events()).hasSize(1);

        var event = response.events().get(0);
        assertThat(event.id()).isEqualTo("441425");
        assertThat(event.title()).isEqualTo("West Ham vs Tottenham");
        assertThat(event.sport()).isEqualTo("Soccer");
        assertThat(event.league()).isEqualTo("English Premier League");
        assertThat(event.season()).isEqualTo("2014-2015");
        assertThat(event.eventDate()).isEqualTo("2014-08-16");
        assertThat(event.thumbnailUrl())
                .isEqualTo("https://example.com/match.jpg");

        server.verify();
    }
}