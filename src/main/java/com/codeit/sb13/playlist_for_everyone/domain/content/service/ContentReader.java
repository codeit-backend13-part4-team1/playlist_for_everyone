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

        Map<UUID, Content> contentById = contentRepository.findAllByIds(contentIds)
                .stream()
                .collect(Collectors.toMap(
                        Content::getId,
                        Function.identity()
                ));

        Map<UUID, ContentRatingProjection> ratingById =
                reviewRepository.findRatingsByContentIds(contentIds)
                        .stream()
                        .collect(Collectors.toMap(
                                ContentRatingProjection::getContentId,
                                Function.identity()
                        ));

        return contentIds.stream()
                .map(contentById::get)
                .filter(Objects::nonNull)
                .map(content -> {
                    ContentRatingProjection rating = ratingById.get(content.getId());

                    double averageRating = rating == null || rating.getAverageRating() == null
                            ? 0.0
                            : rating.getAverageRating();

                    int reviewCount = rating == null || rating.getReviewCount() == null
                            ? 0
                            : Math.toIntExact(rating.getReviewCount());

                    ContentSummary summary = new ContentSummary(
                            content.getId(),
                            content.getType(),
                            content.getTitle(),
                            content.getDescription(),
                            content.getThumbnailUrl(),
                            content.getTags().stream()
                                    .map(ContentTag::getTag)
                                    .toList(),
                            averageRating,
                            reviewCount
                    );

                    FileInfo fileInfo = content.getFileInfo();

                    return new ContentResult<>(
                            summary,
                            fileInfo != null ? fileInfo.getKey() : null
                    );
                })
                .toList();
    }

}
