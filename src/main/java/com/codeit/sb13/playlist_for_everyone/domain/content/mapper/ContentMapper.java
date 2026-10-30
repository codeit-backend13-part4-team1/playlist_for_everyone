package com.codeit.sb13.playlist_for_everyone.domain.content.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import org.springframework.stereotype.Component;

@Component
public class ContentMapper {

    public ContentDto toDto(
            Content content,
            double averageRating,
            int reviewCount,
            long watcherCount
    ) {
        return new ContentDto(
                content.getId(),
                content.getType(),
                content.getTitle(),
                content.getDescription(),
                content.getThumbnailUrl(),
                content.getTags().stream()
                        .map(ContentTag::getTag)
                        .toList(),
                averageRating,
                reviewCount,
                watcherCount
        );
    }

}
