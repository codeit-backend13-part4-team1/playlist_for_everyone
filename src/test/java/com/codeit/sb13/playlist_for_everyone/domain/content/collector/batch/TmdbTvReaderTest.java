package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbPageResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbTvResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 수집할_페이지_수는_1_이상이어야_한다(int maxPages) {
        TmdbClient client = mock(TmdbClient.class);

        assertThatThrownBy(() -> new TmdbTvReader(client, maxPages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수집할 페이지 수는 1 이상이어야 합니다.");

        verifyNoInteractions(client);
    }

    @Test
    void 재시작하면_마지막으로_기록한_TV_위치부터_읽는다() {
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

        var context = new ExecutionContext();
        var reader = new TmdbTvReader(client, 5);

        reader.open(context);

        assertThat(reader.read()).isEqualTo(first);
        reader.update(context);

        // 두 번째 항목은 읽었지만 처리 완료 위치는 기록하지 않았다.
        assertThat(reader.read()).isEqualTo(second);
        reader.close();

        var restartedReader = new TmdbTvReader(client, 5);
        restartedReader.open(context);

        assertThat(restartedReader.read()).isEqualTo(second);
        assertThat(restartedReader.read()).isNull();

        restartedReader.close();
    }
}