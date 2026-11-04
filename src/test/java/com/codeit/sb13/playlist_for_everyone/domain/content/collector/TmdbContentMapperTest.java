package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbTvResponse;
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

    @Test
    void TMDB_TV_응답의_name을_제목으로_사용하고_타입을_TV시리즈로_설정한다() {
        var tv = new TmdbTvResponse(
                550L, "테스트 시리즈", "시리즈 설명", "/tv.jpg", List.of()
        );

        var data = new TmdbContentMapper().fromTv(tv);

        assertThat(data.title()).isEqualTo("테스트 시리즈");
        assertThat(data.type()).isEqualTo(ContentType.tvSeries);
        assertThat(data.provider()).isEqualTo(ContentSource.TMDB);
        assertThat(data.providerItemId()).isEqualTo("550");
    }

    @Test
    void TV_장르_ID를_한글_태그로_변환한다() {
        var tv = new TmdbTvResponse(
                550L, "테스트 시리즈", "시리즈 설명", "/tv.jpg", List.of(10759, 18)
        );

        var data = new TmdbContentMapper().fromTv(tv);

        assertThat(data.tags()).containsExactlyInAnyOrder("액션·모험", "드라마");
    }

    @Test
    void 영화_설명이_없으면_빈_문자열로_변환한다() {
        var movie = new TmdbMovieResponse(
                550L, "테스트 영화", null, "/poster.jpg", List.of()
        );

        var data = new TmdbContentMapper().fromMovie(movie);

        assertThat(data.description()).isEmpty();
    }

    @Test
    void TV_설명이_없으면_빈_문자열로_변환한다() {
        var tv = new TmdbTvResponse(
                550L, "테스트 시리즈", null, "/tv.jpg", List.of()
        );

        var data = new TmdbContentMapper().fromTv(tv);

        assertThat(data.description()).isEmpty();
    }
}