package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbPageResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
}