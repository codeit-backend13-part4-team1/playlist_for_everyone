package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.SportsDbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventsResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SportsDbEventReaderTest {

    @Test
    void 경기_목록을_한_건씩_읽고_끝나면_null을_반환한다() {
        SportsDbClient client = mock(SportsDbClient.class);

        var first = new SportsDbEventResponse(
                "1001", "첫 번째 경기", "Soccer",
                "English Premier League", "2025-2026",
                "2025-08-16", null
        );
        var second = new SportsDbEventResponse(
                "1002", "두 번째 경기", "Soccer",
                "English Premier League", "2025-2026",
                "2025-08-17", null
        );

        when(client.getEventsBySeason("4328", "2025-2026"))
                .thenReturn(new SportsDbEventsResponse(List.of(first, second)));

        var reader = new SportsDbEventReader(client, "4328", "2025-2026");

        assertThat(reader.read()).isEqualTo(first);
        assertThat(reader.read()).isEqualTo(second);
        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();

        verify(client, times(1)).getEventsBySeason("4328", "2025-2026");
        verifyNoMoreInteractions(client);
    }

    @Test
    void 경기_목록이_비어_있으면_종료한다() {
        SportsDbClient client = mock(SportsDbClient.class);

        when(client.getEventsBySeason("4328", "2025-2026"))
                .thenReturn(new SportsDbEventsResponse(List.of()));

        var reader = new SportsDbEventReader(client, "4328", "2025-2026");

        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();

        verify(client, times(1)).getEventsBySeason("4328", "2025-2026");
        verifyNoMoreInteractions(client);
    }

    @Test
    void 응답이_없으면_예외가_발생한다() {
        SportsDbClient client = mock(SportsDbClient.class);

        when(client.getEventsBySeason("4328", "2025-2026"))
                .thenReturn(null);

        var reader = new SportsDbEventReader(client, "4328", "2025-2026");

        assertThatThrownBy(() -> reader.read())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TheSportsDB 경기 목록 응답이 없습니다.");
    }
}