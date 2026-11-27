package com.codeit.sb13.playlist_for_everyone.domain.content.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import org.springframework.stereotype.Component;

@Component
public class ContentDocumentMapper {

    public ContentDocument toContentDocument(Content content) {
        return ContentDocument.builder()
                .id(content.getId().toString())
                .type(content.getType())
                .title(content.getTitle())
                .description(content.getDescription())
                .tags(content.getTags().stream()
                        .map(ContentTag::getTag)
                        .toList())
                .createdAt(content.getCreatedAt())
                .build();
    }

}