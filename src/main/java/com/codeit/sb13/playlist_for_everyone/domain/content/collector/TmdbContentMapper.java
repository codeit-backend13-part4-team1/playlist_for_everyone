package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.CollectedContentData;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbTvResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TmdbContentMapper {

    private static final Map<Integer, String> MOVIE_GENRES = Map.of(
            28, "액션",
            18, "드라마",
            35, "코미디"
    );

    public CollectedContentData fromMovie(TmdbMovieResponse movie) {
        if (movie.id() == null) {
            throw new IllegalArgumentException("TMDB 영화 ID가 필요합니다.");
        }

        String posterPath = movie.posterPath();
        String thumbnailUrl = posterPath == null || posterPath.isBlank()
                ? null
                : "https://image.tmdb.org/t/p/w500" + posterPath;

        List<String> tags = movie.genreIds() == null
                ? List.of()
                : movie.genreIds().stream()
                  .filter(Objects::nonNull)
                  .map(MOVIE_GENRES::get)
                  .filter(Objects::nonNull)
                  .toList();

        return new CollectedContentData(
                ContentType.movie,
                movie.title(),
                movie.overview(),
                thumbnailUrl,
                ContentSource.TMDB,
                movie.id().toString(),
                tags
        );
    }

    public CollectedContentData fromTv(TmdbTvResponse tv) {
        if (tv.id() == null) {
            throw new IllegalArgumentException("TMDB TV 시리즈 ID가 필요합니다.");
        }

        String posterPath = tv.posterPath();
        String thumbnailUrl = posterPath == null || posterPath.isBlank()
                ? null
                : "https://image.tmdb.org/t/p/w500" + posterPath;

        return new CollectedContentData(
                ContentType.tvSeries,
                tv.name(),
                tv.overview(),
                thumbnailUrl,
                ContentSource.TMDB,
                tv.id().toString(),
                List.of()
        );
    }

}