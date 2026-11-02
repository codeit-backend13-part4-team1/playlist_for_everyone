package com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SportsDbEventsResponse(
        List<SportsDbEventResponse> events
) {
}