package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbTvResponse;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

import java.util.Iterator;

public class TmdbTvReader implements ItemStreamReader<TmdbTvResponse> {

    private final TmdbClient tmdbClient;
    private final int maxPages;

    private Iterator<TmdbTvResponse> tvShows;

    public TmdbTvReader(TmdbClient tmdbClient, int maxPages) {
        this.tmdbClient = tmdbClient;
        this.maxPages = maxPages;
    }

    @Override
    public TmdbTvResponse read() {
        if (tvShows == null) {
            var response = tmdbClient.getPopularTvShows(1);

            if (response == null || response.results() == null) {
                throw new IllegalStateException(
                        "TMDB TV 목록 응답이 없습니다."
                );
            }

            tvShows = response.results().iterator();
        }

        if (tvShows.hasNext()) {
            return tvShows.next();
        }

        return null;
    }
}