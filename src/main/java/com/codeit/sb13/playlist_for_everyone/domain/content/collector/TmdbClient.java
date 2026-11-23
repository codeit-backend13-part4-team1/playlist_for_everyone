package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbPageResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbTvResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TmdbClient {

    private final RestClient restClient;
    private final ExternalApiRetry externalApiRetry;

    public TmdbClient(
            @Qualifier("tmdbRestClient") RestClient restClient,
            ExternalApiRetry externalApiRetry
    ) {
        this.restClient = restClient;
        this.externalApiRetry = externalApiRetry;
    }

    /** 인기 영화 목록을 페이지별로 조회합니다. */
    public TmdbPageResponse<TmdbMovieResponse> getPopularMovies(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("페이지 번호는 1 이상이어야 합니다.");
        }

        return externalApiRetry.execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/movie/popular")
                        .queryParam("language", "ko-KR")
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<
                        TmdbPageResponse<TmdbMovieResponse>>() {}));
    }

    /** 인기 TV 프로그램 목록을 페이지별로 조회합니다. */
    public TmdbPageResponse<TmdbTvResponse> getPopularTvShows(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("페이지 번호는 1 이상이어야 합니다.");
        }

        return externalApiRetry.execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tv/popular")
                        .queryParam("language", "ko-KR")
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<
                        TmdbPageResponse<TmdbTvResponse>>() {}));
    }

}