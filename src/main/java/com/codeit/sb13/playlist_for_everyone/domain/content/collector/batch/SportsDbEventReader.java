package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.SportsDbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import org.springframework.batch.infrastructure.item.ItemReader;

import java.util.Iterator;

public class SportsDbEventReader implements ItemReader<SportsDbEventResponse> {

    private final SportsDbClient client;
    private final String leagueId;
    private final String season;

    private Iterator<SportsDbEventResponse> events;

    public SportsDbEventReader(
            SportsDbClient client,
            String leagueId,
            String season
    ) {
        this.client = client;
        this.leagueId = leagueId;
        this.season = season;
    }

    @Override
    public SportsDbEventResponse read() {
        if (events == null) {
            var response = client.getEventsBySeason(leagueId, season);
            events = response.events().iterator();
        }

        return events.hasNext() ? events.next() : null;
    }
}