package com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SportsDbEventsResponse(
        List<SportsDbEventResponse> events
) {
    /** 경기 목록이 없으면 빈 목록으로 처리합니다. */
    public SportsDbEventsResponse {
        events = events == null ? List.of() : events;
    }
}