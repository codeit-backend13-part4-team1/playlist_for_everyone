package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbPageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TmdbMovieReaderTest {

    @Test
    void 영화_목록을_한_건씩_읽고_끝나면_null을_반환한다() {
        TmdbClient client = mock(TmdbClient.class);

        var first = new TmdbMovieResponse(
                1L, "첫 번째 영화", "설명", "/first.jpg", List.of(28)
        );
        var second = new TmdbMovieResponse(
                2L, "두 번째 영화", "설명", "/second.jpg", List.of(18)
        );

        when(client.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(first, second), 1, 2
                ));

        var reader = new TmdbMovieReader(client);

        assertThat(reader.read()).isEqualTo(first);
        assertThat(reader.read()).isEqualTo(second);
        assertThat(reader.read()).isNull();

        verify(client, times(1)).getPopularMovies(1);
        verifyNoMoreInteractions(client);
    }

    @Test
    void 현재_페이지를_다_읽으면_다음_페이지를_조회한다() {
        TmdbClient client = mock(TmdbClient.class);

        var first = new TmdbMovieResponse(
                1L, "첫 번째 영화", "설명", "/first.jpg", List.of(28)
        );
        var second = new TmdbMovieResponse(
                2L, "두 번째 영화", "설명", "/second.jpg", List.of(18)
        );

        when(client.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(first), 2, 2
                ));
        when(client.getPopularMovies(2))
                .thenReturn(new TmdbPageResponse<>(
                        2, List.of(second), 2, 2
                ));

        var reader = new TmdbMovieReader(client);

        assertThat(reader.read()).isEqualTo(first);

        // 현재 페이지를 다 읽기 전에는 다음 페이지를 조회하지 않는다.
        verify(client, never()).getPopularMovies(2);

        assertThat(reader.read()).isEqualTo(second);
        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();

        verify(client, times(1)).getPopularMovies(1);
        verify(client, times(1)).getPopularMovies(2);
        verifyNoMoreInteractions(client);
    }

    @Test
    void 수집_제한에_도달하면_다음_페이지를_조회하지_않는다() {
        TmdbClient client = mock(TmdbClient.class);

        var first = new TmdbMovieResponse(
                1L, "첫 번째 영화", "설명", "/first.jpg", List.of(28)
        );
        var second = new TmdbMovieResponse(
                2L, "두 번째 영화", "설명", "/second.jpg", List.of(18)
        );

        when(client.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(first), 2, 2
                ));
        when(client.getPopularMovies(2))
                .thenReturn(new TmdbPageResponse<>(
                        2, List.of(second), 2, 2
                ));

        var reader = new TmdbMovieReader(client, 1);

        assertThat(reader.read()).isEqualTo(first);
        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();

        verify(client, times(1)).getPopularMovies(1);
        verify(client, never()).getPopularMovies(2);
    }

    @Test
    void 전체_페이지가_수집_제한보다_적으면_마지막_페이지에서_종료한다() {
        TmdbClient client = mock(TmdbClient.class);

        var movie = new TmdbMovieResponse(
                1L, "영화", "설명", "/poster.jpg", List.of(28)
        );

        when(client.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(movie), 1, 1
                ));

        var reader = new TmdbMovieReader(client, 5);

        assertThat(reader.read()).isEqualTo(movie);
        assertThat(reader.read()).isNull();

        verify(client, times(1)).getPopularMovies(1);
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 수집할_페이지_수는_1_이상이어야_한다(int maxPages) {
        TmdbClient client = mock(TmdbClient.class);

        assertThatThrownBy(() -> new TmdbMovieReader(client, maxPages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수집할 페이지 수는 1 이상이어야 합니다.");

        verifyNoInteractions(client);
    }
}