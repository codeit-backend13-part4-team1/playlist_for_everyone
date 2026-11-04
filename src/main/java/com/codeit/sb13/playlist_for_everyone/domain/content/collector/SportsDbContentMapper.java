package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.CollectedContentData;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;

import java.util.stream.Stream;

public class SportsDbContentMapper {

    /** 스포츠 응답을 저장용 데이터로 변환 */
    public CollectedContentData fromEvent(SportsDbEventResponse event) {
        return new CollectedContentData(
                ContentType.sport,
                event.title(),
                "",
                event.thumbnailUrl(),
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