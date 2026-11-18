package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbPageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.infrastructure.item.ExecutionContext;

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

    @Test
    void 영화가_없으면_null을_반환하고_종료한다() {
        TmdbClient client = mock(TmdbClient.class);

        when(client.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<TmdbMovieResponse>(
                        1, List.of(), 0, 0
                ));

        var reader = new TmdbMovieReader(client, 5);

        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();

        verify(client, times(1)).getPopularMovies(1);
        verifyNoMoreInteractions(client);
    }

    @Test
    void 응답이_없으면_예외가_발생한다() {
        TmdbClient client = mock(TmdbClient.class);

        when(client.getPopularMovies(1)).thenReturn(null);

        var reader = new TmdbMovieReader(client, 5);

        assertThatThrownBy(reader::read)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TMDB 영화 목록 응답이 없습니다.");
    }

    @Test
    void 응답의_results가_null이면_예외가_발생한다() {
        TmdbClient client = mock(TmdbClient.class);

        when(client.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<TmdbMovieResponse>(
                        1, null, 1, 0
                ));

        var reader = new TmdbMovieReader(client, 5);

        assertThatThrownBy(reader::read)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TMDB 영화 목록 응답이 없습니다.");
    }

    @Test
    void 재시작하면_마지막으로_기록한_위치부터_읽는다() {
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

        var context = new ExecutionContext();
        var reader = new TmdbMovieReader(client, 5);

        reader.open(context);

        assertThat(reader.read()).isEqualTo(first);
        reader.update(context);

        // 두 번째 영화는 읽었지만 처리 완료 위치는 기록하지 않았다.
        assertThat(reader.read()).isEqualTo(second);
        reader.close();

        var restartedReader = new TmdbMovieReader(client, 5);
        restartedReader.open(context);

        assertThat(restartedReader.read()).isEqualTo(second);
        assertThat(restartedReader.read()).isNull();

        restartedReader.close();
    }

    @Test
    void 두_번째_페이지에서도_기록한_위치부터_다시_읽는다() {
        TmdbClient client = mock(TmdbClient.class);

        var first = new TmdbMovieResponse(
                1L, "첫 번째 영화", "설명", "/first.jpg", List.of(28)
        );
        var second = new TmdbMovieResponse(
                2L, "두 번째 영화", "설명", "/second.jpg", List.of(18)
        );
        var third = new TmdbMovieResponse(
                3L, "세 번째 영화", "설명", "/third.jpg", List.of(35)
        );

        when(client.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(first), 2, 3
                ));
        when(client.getPopularMovies(2))
                .thenReturn(new TmdbPageResponse<>(
                        2, List.of(second, third), 2, 3
                ));

        var context = new ExecutionContext();
        var reader = new TmdbMovieReader(client, 5);

        reader.open(context);
        assertThat(reader.read()).isEqualTo(first);
        assertThat(reader.read()).isEqualTo(second);
        reader.update(context);
        reader.close();

        var restartedReader = new TmdbMovieReader(client, 5);

        restartedReader.open(context);
        assertThat(restartedReader.read()).isEqualTo(third);
        assertThat(restartedReader.read()).isNull();
        restartedReader.close();

        // 재시작할 때는 1페이지를 다시 조회하지 않는다.
        verify(client, times(1)).getPopularMovies(1);
        verify(client, times(2)).getPopularMovies(2);
        verifyNoMoreInteractions(client);
    }

    @Test
    void 읽기를_마친_위치에서_재시작하면_API를_다시_호출하지_않는다() {
        TmdbClient client = mock(TmdbClient.class);

        var movie = new TmdbMovieResponse(
                1L, "영화", "설명", "/poster.jpg", List.of(28)
        );

        when(client.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(movie), 1, 1
                ));

        var context = new ExecutionContext();
        var reader = new TmdbMovieReader(client, 5);

        reader.open(context);
        assertThat(reader.read()).isEqualTo(movie);
        assertThat(reader.read()).isNull();
        reader.update(context);
        reader.close();

        var restartedReader = new TmdbMovieReader(client, 5);

        restartedReader.open(context);
        assertThat(restartedReader.read()).isNull();
        restartedReader.close();

        verify(client, times(1)).getPopularMovies(1);
        verifyNoMoreInteractions(client);
    }

    @Test
    void 저장된_영화_읽기_위치가_응답_크기를_초과하면_예외가_발생한다() {
        TmdbClient client = mock(TmdbClient.class);

        var movie = new TmdbMovieResponse(
                1L, "첫 번째 영화", "설명", "/first.jpg", List.of(28)
        );

        when(client.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(movie), 1, 1
                ));

        var context = new ExecutionContext();
        context.putInt("tmdbMovieReader.index", 2);

        var reader = new TmdbMovieReader(client, 5);
        reader.open(context);

        assertThatThrownBy(reader::read)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TMDB 영화 목록이 변경되어 읽기 위치를 복원할 수 없습니다.");

        reader.close();

        verify(client, times(1)).getPopularMovies(1);
        verifyNoMoreInteractions(client);
    }
}