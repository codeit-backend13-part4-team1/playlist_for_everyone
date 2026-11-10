package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.SportsDbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import org.springframework.batch.infrastructure.item.ItemReader;

public class SportsDbEventReader implements ItemReader<SportsDbEventResponse> {

    public SportsDbEventReader(
            SportsDbClient client,
            String leagueId,
            String season
    ) {
    }

    @Override
    public SportsDbEventResponse read() {
        return null;
    }
}