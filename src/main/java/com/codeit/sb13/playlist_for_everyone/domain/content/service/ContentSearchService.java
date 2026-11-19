package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentSearchService {

    private final ContentSearchCacheService cacheService;
    private final ViewingSessionService viewingSessionService;
    private final FileService fileService;
    private final ContentMapper contentMapper;

    public CursorResponse<ContentDto> search(ContentSearchCondition condition) {
        CursorResponse<ContentResult<ContentDto>> response = cacheService.search(condition);

        boolean sortByWatcherCount = "watcherCount".equals(condition.sortBy());

        List<UUID> contentIds = response.data().stream()
                .map(result -> result.dto().id())
                .toList();

        // 시청자 수 정렬은 기존 조회 값을 사용하고, 다른 정렬은 최신 값 조회
        Map<UUID, Long> viewerCounts = sortByWatcherCount || contentIds.isEmpty()
                ? Map.of()
                : viewingSessionService.getCurrentViewerCountWithContents(contentIds);

        List<ContentDto> contents = response.data().stream()
                .map(result -> {
                    ContentDto dto = result.dto();

                    String thumbnailUrl = result.fileKey() != null
                            ? fileService.getFileUrlByKey(result.fileKey())
                            : result.dto().thumbnailUrl();

                    long watcherCount = sortByWatcherCount
                            ? dto.watcherCount()
                            : viewerCounts.getOrDefault(dto.id(), 0L);

                    return contentMapper.toContentDto(
                            result.dto(),
                            thumbnailUrl,
                            watcherCount
                    );
                })
                .toList();

        return new CursorResponse<>(
                contents,
                response.nextCursor(),
                response.nextIdAfter(),
                response.hasNext(),
                response.totalCount(),
                response.sortBy(),
                response.sortDirection()
        );
    }

}
