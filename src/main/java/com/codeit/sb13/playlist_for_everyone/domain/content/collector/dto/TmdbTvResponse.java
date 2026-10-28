package com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTvResponse(
        Long id,
        String name,
        String overview,

        @JsonProperty("poster_path")
        String posterPath,

        @JsonProperty("genre_ids")
        List<Integer> genreIds
) {
}