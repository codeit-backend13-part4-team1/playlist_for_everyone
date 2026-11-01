package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.TmdbClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ActiveProfiles("test")
@RestClientTest(
        value = TmdbClient.class,
        properties = {
                "everyone.tmdb.base-url=https://tmdb.test/3",
                "everyone.tmdb.access-token=test-token"
        }
)
@Import(TmdbClientConfig.class)
@AutoConfigureMockRestServiceServer(enabled = false)
class TmdbClientTest {

    @Autowired
    @Qualifier("tmdbRestClient")
    private RestClient configuredRestClient;

    private TmdbClient tmdbClient;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        var builder = configuredRestClient.mutate();

        server = MockRestServiceServer.bindTo(builder).build();
        tmdbClient = new TmdbClient(builder.build());
    }

    @Test
    void 인기_영화_목록을_조회한다() {
        // given: 예상하는 요청과 돌려줄 가짜 응답
        server.expect(requestTo(
                        "https://tmdb.test/3/movie/popular?language=ko-KR&page=1"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION, "Bearer test-token"
                ))
                .andExpect(header(
                        HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE
                ))
                .andRespond(withSuccess("""
                        {
                          "page": 1,
                          "results": [
                            {
                              "id": 123,
                              "title": "테스트 영화",
                              "overview": "테스트 영화 설명",
                              "poster_path": "/test-poster.jpg",
                              "genre_ids": [18, 35],
                              "vote_count": 100
                            }
                          ],
                          "total_pages": 3,
                          "total_results": 41
                        }
                        """, MediaType.APPLICATION_JSON));

        // when: 실제로 작성한 조회 메서드 실행
        var response = tmdbClient.getPopularMovies(1);

        // then: 응답이 DTO에 제대로 담겼는지 확인
        assertThat(response).isNotNull();
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.totalResults()).isEqualTo(41);
        assertThat(response.results()).hasSize(1);

        var movie = response.results().get(0);

        assertThat(movie.id()).isEqualTo(123L);
        assertThat(movie.title()).isEqualTo("테스트 영화");
        assertThat(movie.overview()).isEqualTo("테스트 영화 설명");
        assertThat(movie.posterPath()).isEqualTo("/test-poster.jpg");
        assertThat(movie.genreIds()).containsExactly(18, 35);

        server.verify();
    }

    @Test
    void 인기_TV_목록을_조회한다() {
        // given
        server.expect(requestTo(
                        "https://tmdb.test/3/tv/popular?language=ko-KR&page=2"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION, "Bearer test-token"
                ))
                .andExpect(header(
                        HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE
                ))
                .andRespond(withSuccess("""
                    {
                      "page": 2,
                      "results": [
                        {
                          "id": 456,
                          "name": "테스트 TV 프로그램",
                          "overview": "테스트 프로그램 설명",
                          "poster_path": "/tv-poster.jpg",
                          "genre_ids": [18, 35],
                          "vote_count": 200
                        }
                      ],
                      "total_pages": 5,
                      "total_results": 81
                    }
                    """, MediaType.APPLICATION_JSON));

        // when
        var response = tmdbClient.getPopularTvShows(2);

        // then
        assertThat(response).isNotNull();
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(5);
        assertThat(response.totalResults()).isEqualTo(81);
        assertThat(response.results()).hasSize(1);

        var tv = response.results().get(0);

        assertThat(tv.id()).isEqualTo(456L);
        assertThat(tv.name()).isEqualTo("테스트 TV 프로그램");
        assertThat(tv.overview()).isEqualTo("테스트 프로그램 설명");
        assertThat(tv.posterPath()).isEqualTo("/tv-poster.jpg");
        assertThat(tv.genreIds()).containsExactly(18, 35);

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 영화_조회_페이지가_1보다_작으면_예외가_발생한다(int page) {
        assertThatThrownBy(() -> tmdbClient.getPopularMovies(page))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("페이지 번호는 1 이상이어야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void TV_조회_페이지가_1보다_작으면_예외가_발생한다(int page) {
        assertThatThrownBy(() -> tmdbClient.getPopularTvShows(page))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("페이지 번호는 1 이상이어야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void 영화_조회_API_오류를_예외로_전달한다(int statusCode) {
        // given
        server.expect(requestTo(
                        "https://tmdb.test/3/movie/popular?language=ko-KR&page=1"
                ))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode)));

        // when & then
        assertThatThrownBy(() -> tmdbClient.getPopularMovies(1))
                .isInstanceOfSatisfying(
                        RestClientResponseException.class,
                        exception -> assertThat(exception.getStatusCode().value())
                                .isEqualTo(statusCode)
                );

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void TV_조회_API_오류를_예외로_전달한다(int statusCode) {
        // given
        server.expect(requestTo(
                        "https://tmdb.test/3/tv/popular?language=ko-KR&page=1"
                ))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode)));

        // when & then
        assertThatThrownBy(() -> tmdbClient.getPopularTvShows(1))
                .isInstanceOfSatisfying(
                        RestClientResponseException.class,
                        exception -> assertThat(exception.getStatusCode().value())
                                .isEqualTo(statusCode)
                );

        server.verify();
    }

    @Test
    void 영화_응답의_일부_필드가_없어도_조회할_수_있다() {
        server.expect(requestTo(
                        "https://tmdb.test/3/movie/popular?language=ko-KR&page=1"
                ))
                .andRespond(withSuccess("""
                    {
                      "page": 1,
                      "results": [
                        {
                          "id": 123,
                          "title": "정보가 부족한 영화"
                        }
                      ],
                      "total_pages": 1,
                      "total_results": 1
                    }
                    """, MediaType.APPLICATION_JSON));

        var response = tmdbClient.getPopularMovies(1);

        assertThat(response).isNotNull();
        assertThat(response.results()).hasSize(1);

        var movie = response.results().get(0);
        assertThat(movie.id()).isEqualTo(123L);
        assertThat(movie.title()).isEqualTo("정보가 부족한 영화");
        assertThat(movie.overview()).isNull();
        assertThat(movie.posterPath()).isNull();
        assertThat(movie.genreIds()).isNull();

        server.verify();
    }

    @Test
    void TV_응답의_일부_필드가_없어도_조회할_수_있다() {
        server.expect(requestTo(
                        "https://tmdb.test/3/tv/popular?language=ko-KR&page=1"
                ))
                .andRespond(withSuccess("""
                    {
                      "page": 1,
                      "results": [
                        {
                          "id": 456,
                          "name": "정보가 부족한 TV 프로그램"
                        }
                      ],
                      "total_pages": 1,
                      "total_results": 1
                    }
                    """, MediaType.APPLICATION_JSON));

        var response = tmdbClient.getPopularTvShows(1);

        assertThat(response).isNotNull();
        assertThat(response.results()).hasSize(1);

        var tvShow = response.results().get(0);
        assertThat(tvShow.id()).isEqualTo(456L);
        assertThat(tvShow.name()).isEqualTo("정보가 부족한 TV 프로그램");
        assertThat(tvShow.overview()).isNull();
        assertThat(tvShow.posterPath()).isNull();
        assertThat(tvShow.genreIds()).isNull();

        server.verify();
    }
}