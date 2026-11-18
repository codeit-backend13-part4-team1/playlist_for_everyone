package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

import java.util.List;

public class TmdbMovieReader implements ItemStreamReader<TmdbMovieResponse> {

    private static final String PAGE_KEY = "tmdbMovieReader.page";
    private static final String INDEX_KEY = "tmdbMovieReader.index";
    private static final String TOTAL_PAGES_KEY = "tmdbMovieReader.totalPages";

    private final TmdbClient tmdbClient;
    private final int maxPages;

    private List<TmdbMovieResponse> movies = List.of();
    private int currentPage = 1;
    private int totalPages = 1;
    private int index = 0;
    private boolean pageLoaded = false;

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

    /** 기록된 읽기 위치 복원 */
    @Override
    public void open(ExecutionContext context) {
        currentPage = context.getInt(PAGE_KEY, 1);
        index = context.getInt(INDEX_KEY, 0);
        totalPages = context.getInt(TOTAL_PAGES_KEY, 1);

        movies = List.of();
        pageLoaded = false;
    }

    @Override
    public TmdbMovieResponse read() {
        while (currentPage <= totalPages && currentPage <= maxPages) {
            if (!pageLoaded) {
                var response = tmdbClient.getPopularMovies(currentPage);

                if (response == null || response.results() == null) {
                    throw new IllegalStateException(
                            "TMDB 영화 목록 응답이 없습니다."
                    );
                }

                movies = response.results();
                totalPages = response.totalPages();
                pageLoaded = true;

                if (index > movies.size()) {
                    throw new IllegalStateException(
                            "TMDB 영화 목록이 변경되어 읽기 위치를 복원할 수 없습니다."
                    );
                }
            }

            if (index < movies.size()) {
                return movies.get(index++);
            }

            currentPage++;
            index = 0;
            pageLoaded = false;
        }

        return null;
    }

    /** 현재 읽기 위치 기록 */
    @Override
    public void update(ExecutionContext context) {
        context.putInt(PAGE_KEY, currentPage);
        context.putInt(INDEX_KEY, index);
        context.putInt(TOTAL_PAGES_KEY, totalPages);
    }
}