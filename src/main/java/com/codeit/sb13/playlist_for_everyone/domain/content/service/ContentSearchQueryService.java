package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSearchResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentSearchQueryService {

    private final ContentRepository contentRepository;
    private final ContentMapper contentMapper;

    @Transactional(readOnly = true)
    public CursorResponse<ContentResult> search(ContentSearchCondition condition) {
        List<ContentSearchResult> results = contentRepository.search(condition);

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
                case "createdAt" ->
                        lastResult.content().getCreatedAt().toString();
                case "rate" ->
                        String.valueOf(lastResult.averageRating());
                default -> throw new IllegalArgumentException(
                        "지원하지 않는 정렬 기준: " + condition.sortBy()
                );
            };

            nextIdAfter = lastResult.content().getId();
        }

        // ContentSearchResult -> ContentResult 변환
        List<ContentResult> contents = page.stream()
                .map(result -> {
                    Content content = result.content();

                    ContentDto dto = contentMapper.toDto(
                            content,
                            content.getThumbnailUrl(),
                            result.averageRating(),
                            result.reviewCount(),
                            0L
                    );

                    String fileKey = content.getFileInfo() != null
                            ? content.getFileInfo().getKey()
                            : null;

                    return new ContentResult(dto, fileKey);
                })
                .toList();

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

}
