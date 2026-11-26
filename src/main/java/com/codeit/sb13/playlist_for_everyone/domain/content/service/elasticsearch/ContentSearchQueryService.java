package com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSearchResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.elasticsearch.ContentDocumentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentSearchQueryService {

    private final ContentRepository contentRepository;
    private final ContentDocumentRepository contentDocumentRepository;
    private final ViewingSessionService viewingSessionService;
    private final ContentMapper contentMapper;

    @Transactional(readOnly = true)
    public CursorResponse<ContentResult<ContentDto>> search(
            ContentSearchCondition condition
    ) {
        List<UUID> contentIds = searchContentIds(condition);

        // 검색 조건에 일치하는 콘텐츠가 없는 경우
        if (contentIds != null && contentIds.isEmpty()) {
            return new CursorResponse<>(
                    List.of(),
                    null,
                    null,
                    false,
                    0L,
                    condition.sortBy(),
                    condition.sortDirection()
            );
        }

        if ("watcherCount".equals(condition.sortBy())) {
            return searchByWatcherCount(condition, contentIds);
        }

        return searchByDatabase(condition, contentIds);
    }

    private CursorResponse<ContentResult<ContentDto>> searchByDatabase(
            ContentSearchCondition condition,
            List<UUID> contentIds
    ) {
        List<ContentSearchResult> results = contentRepository.searchByDatabase(condition, contentIds);

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
        long totalCount = contentRepository.countBySearchCondition(contentIds);

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
            ContentSearchCondition condition,
            List<UUID> matchedContentIds
    ) {
        List<ContentSearchResult> results =
                contentRepository.searchForWatcherCount(matchedContentIds);

        // 전체 검색 결과의 콘텐츠 ID 추출
        List<UUID> contentIds = results.stream()
                .map(result -> result.content().getId())
                .toList();

        // Redis에서 전체 콘텐츠 시청자 수 조회
        Map<UUID, Long> viewerCounts = contentIds.isEmpty()
                ? Map.of()
                : viewingSessionService.getCurrentViewerCountWithContents(contentIds);

        Comparator<ContentSearchResult> comparator =
                Comparator.comparingLong(
                        (ContentSearchResult result) ->
                                viewerCounts.getOrDefault(
                                        result.content().getId(),
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
                                result.content().getId(),
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
                            lastResult.content().getId(),
                            0L
                    )
            );

            nextIdAfter = lastResult.content().getId();
        }

        List<ContentResult<ContentDto>> contents = toContentResults(page, viewerCounts);

        // 전체 검색 결과 개수 조회
        long totalCount = results.size();

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

    // 검색 조건에 일치하는 콘텐츠 ID 조회
    private List<UUID> searchContentIds(
            ContentSearchCondition condition
    ) {
        // 검색 조건이 없으면 Elasticsearch 조회 생략
        if (!hasSearchCondition(condition)) {
            return null;
        }

        return contentDocumentRepository.searchContentIds(condition);
    }

    // 키워드, 유형, 태그 검색 조건 존재 여부 확인
    private boolean hasSearchCondition(ContentSearchCondition condition) {
        return StringUtils.hasText(condition.keywordLike())
                || condition.typeEqual() != null
                || (condition.tagsIn() != null
                && !condition.tagsIn().isEmpty());
    }

    private List<ContentResult<ContentDto>> toContentResults(
            List<ContentSearchResult> page,
            Map<UUID, Long> viewerCounts
    ) {
        return page.stream()
                .map(result -> {
                    Content content = result.content();

                    long watcherCount = viewerCounts.getOrDefault(
                            content.getId(), 0L
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
