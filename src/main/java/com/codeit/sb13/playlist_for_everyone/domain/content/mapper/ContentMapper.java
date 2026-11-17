package com.codeit.sb13.playlist_for_everyone.domain.content.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import org.springframework.stereotype.Component;

@Component
public class ContentMapper {

    public ContentDto toDto(
            Content content,
            String thumbnailUrl,
            double averageRating,
            int reviewCount,
            long watcherCount
    ) {
        return new ContentDto(
                content.getId(),
                content.getType(),
                content.getTitle(),
                content.getDescription(),
                thumbnailUrl,
                content.getTags().stream()
                        .map(ContentTag::getTag)
                        .toList(),
                averageRating,
                reviewCount,
                watcherCount
        );
    }

    public ContentSummary toSummary(
            ContentSummary summary,
            String thumbnailUrl
    ) {
        return new ContentSummary(
                summary.contentId(),
                summary.type(),
                summary.title(),
                summary.description(),
                thumbnailUrl,
                summary.tags(),
                summary.averageRating(),
                summary.reviewCount()
        );
    }

    public ContentDto toContentDto(
            ContentDto dto,
            String thumbnailUrl,
            Long watcherCount
    ) {
        return new ContentDto(
                dto.id(),
                dto.type(),
                dto.title(),
                dto.description(),
                thumbnailUrl != null ? thumbnailUrl : dto.thumbnailUrl(),
                dto.tags(),
                dto.averageRating(),
                dto.reviewCount(),
                watcherCount != null ? watcherCount : dto.watcherCount()
        );
    }

}
