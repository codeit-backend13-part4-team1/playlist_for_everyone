package com.codeit.sb13.playlist_for_everyone.domain.content.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
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
            Content content,
            String thumbnailUrl,
            double averageRating,
            int reviewCount
    ) {
        return new ContentSummary(
                content.getId(),
                content.getType(),
                content.getTitle(),
                content.getDescription(),
                thumbnailUrl,
                content.getTags().stream()
                        .map(ContentTag::getTag)
                        .toList(),
                averageRating,
                reviewCount
        );
    }

    public ContentDto withThumbnailUrl(ContentDto dto, String thumbnailUrl) {
        return new ContentDto(
                dto.id(),
                dto.type(),
                dto.title(),
                dto.description(),
                thumbnailUrl,
                dto.tags(),
                dto.averageRating(),
                dto.reviewCount(),
                dto.watcherCount()
        );
    }

}
