package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbTvResponse;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

public class TmdbTvReader implements ItemStreamReader<TmdbTvResponse> {

    private final TmdbClient tmdbClient;
    private final int maxPages;

    public TmdbTvReader(TmdbClient tmdbClient, int maxPages) {
        this.tmdbClient = tmdbClient;
        this.maxPages = maxPages;
    }

    @Override
    public TmdbTvResponse read() {
        return null;
    }
}