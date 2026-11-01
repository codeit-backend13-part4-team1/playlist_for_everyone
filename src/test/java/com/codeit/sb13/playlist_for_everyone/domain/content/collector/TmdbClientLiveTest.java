package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.TmdbClientConfig;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.TmdbProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
        named = "EVERYONE_TMDB_ACCESS_TOKEN",
        matches = ".+"
)
class TmdbClientLiveTest {

    @Test
    void 실제_TMDB에서_영화와_TV를_조회한다() {
        // 실제 호출에 사용할 설정
        var properties = new TmdbProperties();
        properties.setBaseUrl("https://api.themoviedb.org/3");
        properties.setAccessToken(
                System.getenv("EVERYONE_TMDB_ACCESS_TOKEN")
        );

        // 우리가 작성한 설정과 클라이언트 사용
        var config = new TmdbClientConfig();
        var restClient = config.tmdbRestClient(
                RestClient.builder(), properties
        );
        var client = new TmdbClient(restClient);

        // 실제 영화 조회
        var movies = client.getPopularMovies(1);

        assertThat(movies).isNotNull();
        assertThat(movies.page()).isEqualTo(1);
        assertThat(movies.results()).isNotEmpty();

        // 실제 TV 조회
        var tvShows = client.getPopularTvShows(1);

        assertThat(tvShows).isNotNull();
        assertThat(tvShows.page()).isEqualTo(1);
        assertThat(tvShows.results()).isNotEmpty();

        System.out.println(
                "첫 번째 영화: " + movies.results().get(0).title()
        );
        System.out.println(
                "첫 번째 TV: " + tvShows.results().get(0).name()
        );
    }
}