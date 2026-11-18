package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSearchResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.DpeWatchingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentSearchQueryService {

    private final ContentRepository contentRepository;
    private final DpeWatchingSessionService dpeWatchingSessionService;
    private final ContentMapper contentMapper;

    @Transactional(readOnly = true)
    public CursorResponse<ContentResult<ContentDto>> search(
            ContentSearchCondition condition
    ) {
        if ("watcherCount".equals(condition.sortBy())) {
            return searchByWatcherCount(condition);
        }

        return searchByDatabase(condition);
    }

    private CursorResponse<ContentResult<ContentDto>> searchByDatabase(
            ContentSearchCondition condition
    ) {
        List<ContentSearchResult> results = contentRepository.searchByDatabase(condition);

        // 다음 페이지 존재 여부 확인
        boolean hasNext = results.size() > condition.limit();

        // 요청한 개수만 반환
        List<ContentSearchResult> page = results.stream()
                .limit(condition.limit())
                .toList();

        // 다음 페이지 커서 생성
        String nextCursor = null;
        UUID nextIdAfter = null;

        if (hasNext && !page.isEmpty()) {
            ContentSearchResult lastResult = page.get(page.size() - 1);

            nextCursor = switch (condition.sortBy()) {
                case "createdAt" -> lastResult.content().getCreatedAt().toString();
                case "rate" -> String.valueOf(lastResult.averageRating());
                default -> throw new IllegalArgumentException(
                        "지원하지 않는 정렬 기준: " + condition.sortBy()
                );
            };

            nextIdAfter = lastResult.content().getId();
        }

        List<ContentResult<ContentDto>> contents = toContentResults(page, Map.of());

        // 전체 검색 결과 개수 조회
        long totalCount = contentRepository.countBySearchCondition(condition);

        return new CursorResponse<>(
                contents,
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                condition.sortBy(),
                condition.sortDirection()
        );
    }

    private CursorResponse<ContentResult<ContentDto>> searchByWatcherCount(
            ContentSearchCondition condition
    ) {
        List<ContentSearchResult> results =
                contentRepository.searchForWatcherCount(condition);

        // 전체 검색 결과의 콘텐츠 ID 추출
        List<UUID> contentIds = results.stream()
                .map(result -> result.content().getId())
                .toList();

        // Redis에서 전체 콘텐츠 시청자 수 조회
        Map<String, Long> viewerCounts = contentIds.isEmpty()
                ? Map.of()
                : dpeWatchingSessionService.getCurrentViewerCountWithContents(contentIds);

        Comparator<ContentSearchResult> comparator =
                Comparator.comparingLong(
                        (ContentSearchResult result) ->
                                viewerCounts.getOrDefault(
                                        result.content().getId().toString(),
                                        0L
                                )
                ).thenComparing(
                        result -> result.content().getId()
                );

        if (condition.sortDirection() == SortDirection.DESCENDING) {
            comparator = comparator.reversed();
        }

        List<ContentSearchResult> sorted = results.stream()
                .sorted(comparator)
                .toList();

        // 커서 조건 적용
        List<ContentSearchResult> filtered = sorted;

        if (condition.cursor() != null && condition.idAfter() != null) {
            long cursorCount = Long.parseLong(condition.cursor());
            UUID idAfter = condition.idAfter();

            boolean ascending =
                    condition.sortDirection() == SortDirection.ASCENDING;

            filtered = sorted.stream()
                    .filter(result -> {
                        long watcherCount = viewerCounts.getOrDefault(
                                result.content().getId().toString(),
                                0L
                        );

                        UUID id = result.content().getId();

                        return ascending
                                ? watcherCount > cursorCount
                                  || (watcherCount == cursorCount
                                      && id.compareTo(idAfter) > 0)
                                : watcherCount < cursorCount
                                  || (watcherCount == cursorCount
                                      && id.compareTo(idAfter) < 0);
                    })
                    .toList();
        }

        // 다음 페이지 존재 여부 확인
        boolean hasNext = filtered.size() > condition.limit();

        // 요청한 개수만 반환
        List<ContentSearchResult> page = filtered.stream()
                .limit(condition.limit())
                .toList();

        // 다음 페이지 커서 생성
        String nextCursor = null;
        UUID nextIdAfter = null;

        if (hasNext && !page.isEmpty()) {
            ContentSearchResult lastResult = page.get(page.size() - 1);

            nextCursor = String.valueOf(
                    viewerCounts.getOrDefault(
                            lastResult.content().getId().toString(),
                            0L
                    )
            );

            nextIdAfter = lastResult.content().getId();
        }

        List<ContentResult<ContentDto>> contents = toContentResults(page, viewerCounts);

        // 전체 검색 결과 개수 조회
        long totalCount = contentRepository.countBySearchCondition(condition);

        return new CursorResponse<>(
                contents,
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                condition.sortBy(),
                condition.sortDirection()
        );
    }

    private List<ContentResult<ContentDto>> toContentResults(
            List<ContentSearchResult> page,
            Map<String, Long> viewerCounts
    ) {
        return page.stream()
                .map(result -> {
                    Content content = result.content();

                    long watcherCount = viewerCounts.getOrDefault(
                            content.getId().toString(), 0L
                    );

                    ContentDto dto = contentMapper.toDto(
                            content,
                            content.getThumbnailUrl(),
                            result.averageRating(),
                            result.reviewCount(),
                            watcherCount
                    );

                    String fileKey = content.getFileInfo() != null
                            ? content.getFileInfo().getKey()
                            : null;

                    return new ContentResult<>(dto, fileKey);
                })
                .toList();
    }

}
