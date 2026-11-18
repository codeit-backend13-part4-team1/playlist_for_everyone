package com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SportsDbEventResponse(
        @JsonProperty("idEvent")
        String id,

        @JsonProperty("strEvent")
        String title,

        @JsonProperty("strSport")
        String sport,

        @JsonProperty("strLeague")
        String league,

        @JsonProperty("strSeason")
        String season,

        @JsonProperty("dateEvent")
        String eventDate,

        @JsonProperty("strThumb")
        String thumbnailUrl
) {
}