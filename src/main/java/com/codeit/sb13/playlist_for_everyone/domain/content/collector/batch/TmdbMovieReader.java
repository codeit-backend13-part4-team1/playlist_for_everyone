package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import org.springframework.batch.infrastructure.item.ItemReader;

import java.util.Iterator;

public class TmdbMovieReader implements ItemReader<TmdbMovieResponse> {

    private final TmdbClient tmdbClient;
    private Iterator<TmdbMovieResponse> movies;

    public TmdbMovieReader(TmdbClient tmdbClient) {
        this.tmdbClient = tmdbClient;
    }

    @Override
    public TmdbMovieResponse read() {
        if (movies == null) {
            movies = tmdbClient.getPopularMovies(1)
                    .results()
                    .iterator();
        }

        if (movies.hasNext()) {
            return movies.next();
        }

        return null;
    }
}