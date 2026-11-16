package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentReader {

    private final ContentRepository contentRepository;
    private final WatchingSessionService watchingSessionService;
    private final ContentMapper contentMapper;

    @Transactional(readOnly = true)
    public ContentResult getById(UUID contentId) {
        ContentDetailProjection detail =
                contentRepository.findDetailById(contentId)
                        .orElseThrow(() -> new ContentNotFoundException(
                                Map.of("contentId", contentId)
                        ));

        Content content = detail.getContent();
        FileInfo fileInfo = content.getFileInfo();

        ContentDto dto = contentMapper.toDto(
                content,
                content.getThumbnailUrl(),
                detail.getAverageRating(),
                Math.toIntExact(detail.getReviewCount()),
                watchingSessionService.getCurrentViewerCount(contentId)
        );

        return new ContentResult(
                dto,
                fileInfo != null ? fileInfo.getKey() : null
        );
    }

}
