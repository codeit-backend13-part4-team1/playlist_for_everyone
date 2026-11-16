package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentSearchService {

    private final ContentSearchCacheService cacheService;
    private final FileService fileService;
    private final ContentMapper contentMapper;

    public CursorResponse<ContentDto> search(ContentSearchCondition condition) {
        CursorResponse<ContentResult> response = cacheService.search(condition);

        List<ContentDto> contents = response.data().stream()
                .map(result -> {
                    String thumbnailUrl = result.fileKey() != null
                            ? fileService.getFileUrlByKey(result.fileKey())
                            : result.dto().thumbnailUrl();

                    return contentMapper.toContentDto(
                            result.dto(),
                            thumbnailUrl,
                            null
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
