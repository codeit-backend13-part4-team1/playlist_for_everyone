package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbTvResponse;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

import java.util.Collections;
import java.util.Iterator;

public class TmdbTvReader implements ItemStreamReader<TmdbTvResponse> {

    private final TmdbClient tmdbClient;
    private final int maxPages;

    private Iterator<TmdbTvResponse> tvShows = Collections.emptyIterator();
    private int nextPage = 1;
    private int totalPages = 1;

    public TmdbTvReader(TmdbClient tmdbClient, int maxPages) {
        this.tmdbClient = tmdbClient;
        this.maxPages = maxPages;
    }

    @Override
    public TmdbTvResponse read() {
        while (!tvShows.hasNext()) {
            if (nextPage > totalPages || nextPage > maxPages) {
                return null;
            }

            var response = tmdbClient.getPopularTvShows(nextPage);

            if (response == null || response.results() == null) {
                throw new IllegalStateException(
                        "TMDB TV 목록 응답이 없습니다."
                );
            }

            tvShows = response.results().iterator();
            totalPages = response.totalPages();
            nextPage++;
        }

        return tvShows.next();
    }
}