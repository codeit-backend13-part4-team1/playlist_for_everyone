package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.CollectedContentData;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SportsDbContentMapper {

    /** 스포츠 응답을 저장용 데이터로 변환 */
    public CollectedContentData fromEvent(SportsDbEventResponse event) {

        if (event.id() == null || event.id().isBlank()) {
            throw new IllegalArgumentException("TheSportsDB 경기 ID가 필요합니다.");
        }

        String description = Stream.of(
                        event.league(),
                        event.season(),
                        event.eventDate()
                )
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" / "));

        String thumbnailUrl = event.thumbnailUrl();

        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            thumbnailUrl = null;
        }

        return new CollectedContentData(
                ContentType.sport,
                event.title(),
                description,
                thumbnailUrl,
                ContentSource.THE_SPORTS_DB,
                event.id(),
                Stream.of(event.sport(), event.league())
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList()
        );
    }
}