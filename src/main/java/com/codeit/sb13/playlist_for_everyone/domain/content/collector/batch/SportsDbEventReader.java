package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.SportsDbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

import java.util.List;

public class SportsDbEventReader
        implements ItemStreamReader<SportsDbEventResponse> {

    private static final String INDEX_KEY = "sportsDbEventReader.index";

    private final SportsDbClient client;
    private final String leagueId;
    private final String season;

    private List<SportsDbEventResponse> events = List.of();
    private int index = 0;
    private boolean loaded = false;

    public SportsDbEventReader(
            SportsDbClient client,
            String leagueId,
            String season
    ) {
        this.client = client;
        this.leagueId = leagueId;
        this.season = season;
    }

    /** 기록된 읽기 위치 복원 */
    @Override
    public void open(ExecutionContext context) {
        index = context.getInt(INDEX_KEY, 0);
        events = List.of();
        loaded = false;
    }

    @Override
    public SportsDbEventResponse read() {
        if (!loaded) {
            var response = client.getEventsBySeason(leagueId, season);

            if (response == null) {
                throw new IllegalStateException(
                        "TheSportsDB 경기 목록 응답이 없습니다."
                );
            }

            events = response.events();

            if (index > events.size()) {
                throw new IllegalStateException(
                        "TheSportsDB 경기 목록이 변경되어 읽기 위치를 복원할 수 없습니다."
                );
            }

            loaded = true;
        }

        if (index < events.size()) {
            return events.get(index++);
        }

        return null;
    }

    /** 현재 읽기 위치 기록 */
    @Override
    public void update(ExecutionContext context) {
        context.putInt(INDEX_KEY, index);
    }
}