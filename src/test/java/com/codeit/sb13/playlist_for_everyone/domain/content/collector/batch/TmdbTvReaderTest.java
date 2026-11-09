package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbPageResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbTvResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TmdbTvReaderTest {

    @Test
    void TV_목록을_한_건씩_읽고_끝나면_null을_반환한다() {
        TmdbClient client = mock(TmdbClient.class);

        var first = new TmdbTvResponse(
                1L, "첫 번째 드라마", "설명", "/first.jpg", List.of(18)
        );
        var second = new TmdbTvResponse(
                2L, "두 번째 드라마", "설명", "/second.jpg", List.of(35)
        );

        when(client.getPopularTvShows(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(first, second), 1, 2
                ));

        var reader = new TmdbTvReader(client, 5);

        assertThat(reader.read()).isEqualTo(first);
        assertThat(reader.read()).isEqualTo(second);
        assertThat(reader.read()).isNull();

        verify(client, times(1)).getPopularTvShows(1);
        verifyNoMoreInteractions(client);
    }

    @Test
    void 현재_페이지를_다_읽으면_다음_TV_페이지를_조회한다() {
        TmdbClient client = mock(TmdbClient.class);

        var first = new TmdbTvResponse(
                1L, "첫 번째 드라마", "설명", "/first.jpg", List.of(18)
        );
        var second = new TmdbTvResponse(
                2L, "두 번째 드라마", "설명", "/second.jpg", List.of(35)
        );

        when(client.getPopularTvShows(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(first), 2, 2
                ));
        when(client.getPopularTvShows(2))
                .thenReturn(new TmdbPageResponse<>(
                        2, List.of(second), 2, 2
                ));

        var reader = new TmdbTvReader(client, 5);

        assertThat(reader.read()).isEqualTo(first);
        verify(client, never()).getPopularTvShows(2);

        assertThat(reader.read()).isEqualTo(second);
        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();

        verify(client, times(1)).getPopularTvShows(1);
        verify(client, times(1)).getPopularTvShows(2);
        verifyNoMoreInteractions(client);
    }

    @Test
    void 수집_제한에_도달하면_다음_TV_페이지를_조회하지_않는다() {
        TmdbClient client = mock(TmdbClient.class);

        var first = new TmdbTvResponse(
                1L, "첫 번째 드라마", "설명", "/first.jpg", List.of(18)
        );
        var second = new TmdbTvResponse(
                2L, "두 번째 드라마", "설명", "/second.jpg", List.of(35)
        );

        when(client.getPopularTvShows(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(first), 2, 2
                ));
        when(client.getPopularTvShows(2))
                .thenReturn(new TmdbPageResponse<>(
                        2, List.of(second), 2, 2
                ));

        var reader = new TmdbTvReader(client, 1);

        assertThat(reader.read()).isEqualTo(first);
        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();

        verify(client, times(1)).getPopularTvShows(1);
        verifyNoMoreInteractions(client);
    }
}