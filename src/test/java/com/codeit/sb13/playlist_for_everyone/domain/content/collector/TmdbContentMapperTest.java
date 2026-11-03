package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbContentMapperTest {

    @Test
    void TMDB_영화를_저장할_때_타입과_출처와_외부_ID를_설정한다() {
        var movie = new TmdbMovieResponse(
                550L, "테스트 영화", "영화 설명", "/poster.jpg", List.of()
        );

        var data = new TmdbContentMapper().fromMovie(movie);

        assertThat(data.type()).isEqualTo(ContentType.movie);
        assertThat(data.provider()).isEqualTo(ContentSource.TMDB);
        assertThat(data.providerItemId()).isEqualTo("550");
    }

    @Test
    void 영화_장르_ID를_한글_태그로_변환한다() {
        var movie = new TmdbMovieResponse(
                550L, "테스트 영화", "영화 설명", "/poster.jpg", List.of(28, 18)
        );

        var data = new TmdbContentMapper().fromMovie(movie);

        assertThat(data.tags()).containsExactlyInAnyOrder("액션", "드라마");
    }

    @Test
    void 알_수_없는_장르가_섞여도_알려진_장르는_태그로_남긴다() {
        var movie = new TmdbMovieResponse(
                550L, "테스트 영화", "영화 설명", "/poster.jpg", List.of(28, 35, -1)
        );

        var data = new TmdbContentMapper().fromMovie(movie);

        assertThat(data.tags()).containsExactlyInAnyOrder("액션", "코미디");
    }
}