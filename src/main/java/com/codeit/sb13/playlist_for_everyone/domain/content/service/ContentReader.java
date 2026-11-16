package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.ContentRatingProjection;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentReader {

    private final ContentRepository contentRepository;
    private final ReviewRepository reviewRepository;
    private final ContentMapper contentMapper;

    @Transactional(readOnly = true)
    public ContentResult<ContentDto> getById(UUID contentId) {
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
                0L
        );

        return new ContentResult<>(
                dto,
                fileInfo != null ? fileInfo.getKey() : null
        );
    }

    @Transactional(readOnly = true)
    public List<ContentResult<ContentSummary>> getSummariesByIds(List<UUID> contentIds) {
        if (contentIds.isEmpty()) {
            return List.of();
        }

        return null;
    }

}
