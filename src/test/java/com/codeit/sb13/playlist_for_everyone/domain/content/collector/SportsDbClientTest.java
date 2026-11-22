package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.SportsDbClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ActiveProfiles("test")
@RestClientTest(value = SportsDbClient.class, properties = {
        "everyone.sportsdb.base-url=https://sportsdb.test",
        "everyone.sportsdb.api-key=test-key"
})
@Import({SportsDbClientConfig.class, ExternalApiRetry.class})
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
        sportsDbClient = new SportsDbClient(builder.build(), new ExternalApiRetry());
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 리그_ID가_없으면_예외가_발생한다(String leagueId) {
        assertThatThrownBy(
                () -> sportsDbClient.getEventsBySeason(leagueId, "2014-2015")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("리그 ID는 필수입니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 시즌이_없으면_예외가_발생한다(String season) {
        assertThatThrownBy(
                () -> sportsDbClient.getEventsBySeason("4328", season)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("시즌은 필수입니다.");
    }

    @Test
    void 경기_응답의_일부_필드가_없어도_조회할_수_있다() {
        server.expect(requestTo(
                        "https://sportsdb.test/test-key/eventsseason.php"
                                + "?id=4328&s=2014-2015"
                ))
                .andRespond(withSuccess("""
                    {
                      "events": [
                        {
                          "idEvent": "441425",
                          "strEvent": "West Ham vs Tottenham",
                          "strThumb": null
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
        assertThat(event.sport()).isNull();
        assertThat(event.league()).isNull();
        assertThat(event.season()).isNull();
        assertThat(event.eventDate()).isNull();
        assertThat(event.thumbnailUrl()).isNull();

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void API_오류_응답을_예외로_전달한다(int statusCode) {
        server.expect(
                        ExpectedCount.times(statusCode == 500 ? 3 : 1),
                        requestTo(
                                "https://sportsdb.test/test-key/eventsseason.php"
                                        + "?id=4328&s=2014-2015"
                        )
                )
                .andRespond(withStatus(HttpStatus.valueOf(statusCode)));

        assertThatThrownBy(
                () -> sportsDbClient.getEventsBySeason("4328", "2014-2015")
        )
                .isInstanceOfSatisfying(
                        RestClientResponseException.class,
                        exception -> assertThat(exception.getStatusCode().value())
                                .isEqualTo(statusCode)
                );

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"events\": null}",
            "{\"events\": []}",
            "{}"
    })
    void 경기_목록이_없으면_빈_목록을_반환한다(String responseBody) {
        server.expect(requestTo(
                        "https://sportsdb.test/test-key/eventsseason.php"
                                + "?id=4328&s=2014-2015"
                ))
                .andRespond(withSuccess(
                        responseBody,
                        MediaType.APPLICATION_JSON
                ));

        var response = sportsDbClient.getEventsBySeason("4328", "2014-2015");

        assertThat(response).isNotNull();
        assertThat(response.events()).isEmpty();

        server.verify();
    }

    @ParameterizedTest
    @MethodSource("networkErrors")
    void 연결_실패_이유는_구분하고_API_키는_노출하지_않는다(
            IOException error, String expectedCode
    ) {
        server.expect(ExpectedCount.times(error instanceof UnknownHostException ? 1 : 3),
                        requestTo("https://sportsdb.test/test-key/eventsseason.php"
                                        + "?id=4328&s=2014-2015"))
                .andRespond(request -> {throw error;});

    }

    private static Stream<Arguments> networkErrors() {
        return Stream.of(
                Arguments.of(new UnknownHostException("test-key"), "DNS_ERROR"),
                Arguments.of(new ConnectException("test-key"), "CONNECTION_ERROR"),
                Arguments.of(new IOException("test-key"), "NETWORK_ERROR")
        );
    }


    @Test
    void 일시적인_서버_오류_후_재시도하면_성공한다() {
        String url = "https://sportsdb.test/test-key/eventsseason.php"
                + "?id=4328&s=2014-2015";

        // 첫 번째 요청: 일시적인 서버 오류
        server.expect(requestTo(url))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // 두 번째 요청: 정상 응답
        server.expect(requestTo(url))
                .andRespond(withSuccess("""
                    {
                      "events": []
                    }
                    """, MediaType.APPLICATION_JSON));

        var response = sportsDbClient.getEventsBySeason(
                "4328", "2014-2015"
        );

        assertThat(response).isNotNull();
        assertThat(response.events()).isEmpty();

        server.verify();
    }


    @Test
    void 일시적인_서버_오류가_계속되면_재시도_횟수_초과로_실패한다() {
        String url = "https://sportsdb.test/test-key/eventsseason.php"
                + "?id=4328&s=2014-2015";

        // 최초 요청 + 재시도 2회 모두 503 반환
        server.expect(ExpectedCount.times(3), requestTo(url))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(
                () -> sportsDbClient.getEventsBySeason("4328", "2014-2015")
        )
                .isInstanceOfSatisfying(
                        RestClientResponseException.class,
                        exception -> assertThat(exception.getStatusCode().value())
                                .isEqualTo(503)
                );

        server.verify();
    }


}