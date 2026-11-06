package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import org.springframework.batch.infrastructure.item.ItemReader;

public class TmdbMovieReader implements ItemReader<TmdbMovieResponse> {

    private final TmdbClient tmdbClient;

    public TmdbMovieReader(TmdbClient tmdbClient) {
        this.tmdbClient = tmdbClient;
    }

    @Override
    public TmdbMovieResponse read() {
        return null;
    }
}