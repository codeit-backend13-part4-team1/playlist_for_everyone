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

    private static final Map<Integer, String> MOVIE_GENRES = Map.ofEntries(
            Map.entry(28, "액션"),
            Map.entry(12, "모험"),
            Map.entry(16, "애니메이션"),
            Map.entry(35, "코미디"),
            Map.entry(80, "범죄"),
            Map.entry(99, "다큐멘터리"),
            Map.entry(18, "드라마"),
            Map.entry(10751, "가족"),
            Map.entry(14, "판타지"),
            Map.entry(36, "역사"),
            Map.entry(27, "공포"),
            Map.entry(10402, "음악"),
            Map.entry(9648, "미스터리"),
            Map.entry(10749, "로맨스"),
            Map.entry(878, "SF"),
            Map.entry(10770, "TV 영화"),
            Map.entry(53, "스릴러"),
            Map.entry(10752, "전쟁"),
            Map.entry(37, "서부")
    );

    private static final Map<Integer, String> TV_GENRES = Map.ofEntries(
            Map.entry(10759, "액션·모험"),
            Map.entry(16, "애니메이션"),
            Map.entry(35, "코미디"),
            Map.entry(80, "범죄"),
            Map.entry(99, "다큐멘터리"),
            Map.entry(18, "드라마"),
            Map.entry(10751, "가족"),
            Map.entry(10762, "키즈"),
            Map.entry(9648, "미스터리"),
            Map.entry(10763, "뉴스"),
            Map.entry(10764, "리얼리티"),
            Map.entry(10765, "SF·판타지"),
            Map.entry(10766, "연속극"),
            Map.entry(10767, "토크쇼"),
            Map.entry(10768, "전쟁·정치"),
            Map.entry(37, "서부")
    );

    public CollectedContentData fromMovie(TmdbMovieResponse movie) {
        if (movie.id() == null) {
            throw new IllegalArgumentException("TMDB 영화 ID가 필요합니다.");
        }

        String thumbnailUrl = toThumbnailUrl(movie.posterPath());

        List<String> tags = toGenreTags(movie.genreIds(), MOVIE_GENRES);

        return new CollectedContentData(
                ContentType.movie,
                movie.title(),
                movie.overview() == null ? "" : movie.overview(),
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

        String thumbnailUrl = toThumbnailUrl(tv.posterPath());

        List<String> tags = toGenreTags(tv.genreIds(), TV_GENRES);

        return new CollectedContentData(
                ContentType.tvSeries,
                tv.name(),
                tv.overview() == null ? "" : tv.overview(),
                thumbnailUrl,
                ContentSource.TMDB,
                tv.id().toString(),
                tags
        );
    }

    private String toThumbnailUrl(String posterPath) {
        if (posterPath == null || posterPath.isBlank()) {
            return null;
        }
        return "https://image.tmdb.org/t/p/w500" + posterPath;
    }

    private List<String> toGenreTags(List<Integer> genreIds, Map<Integer, String> genreNames) {
        if (genreIds == null) {
            return List.of();
        }

        return genreIds.stream()
                .filter(Objects::nonNull)
                .map(genreNames::get)
                .filter(Objects::nonNull)
                .toList();
    }

}