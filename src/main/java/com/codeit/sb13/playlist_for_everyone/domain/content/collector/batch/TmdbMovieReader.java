package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import org.springframework.batch.infrastructure.item.ItemReader;

import java.util.Collections;
import java.util.Iterator;

public class TmdbMovieReader implements ItemReader<TmdbMovieResponse> {

    private final TmdbClient tmdbClient;
    private final int maxPages;

    private Iterator<TmdbMovieResponse> movies = Collections.emptyIterator();
    private int nextPage = 1;
    private int totalPages = 1;

    public TmdbMovieReader(TmdbClient tmdbClient) {
        this(tmdbClient, Integer.MAX_VALUE);
    }

    public TmdbMovieReader(TmdbClient tmdbClient, int maxPages) {
        if (maxPages < 1) {
            throw new IllegalArgumentException(
                    "수집할 페이지 수는 1 이상이어야 합니다."
            );
        }

        this.tmdbClient = tmdbClient;
        this.maxPages = maxPages;
    }

    @Override
    public TmdbMovieResponse read() {
        while (!movies.hasNext()) {
            if (nextPage > totalPages || nextPage > maxPages) {
                return null;
            }

            var response = tmdbClient.getPopularMovies(nextPage);

            if (response == null || response.results() == null) {
                throw new IllegalStateException("TMDB 영화 목록 응답이 없습니다.");
            }

            movies = response.results().iterator();
            totalPages = response.totalPages();
            nextPage++;
        }

        return movies.next();
    }
}