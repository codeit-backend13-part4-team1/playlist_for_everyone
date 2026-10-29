package com.codeit.sb13.playlist_for_everyone.domain.content.entity;

import com.codeit.sb13.playlist_for_everyone.global.domain.UpdatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "contents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_content_provider_type_item_id",
                        columnNames = {"provider", "type", "provider_item_id"}
                )
        }
)
public class Content extends UpdatedAtEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ContentType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url", nullable = false, length = 2048)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private ContentSource provider;

    @Column(name = "provider_item_id")
    private String providerItemId;

    @OneToMany(
            mappedBy = "content",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ContentTag> tags = new ArrayList<>();

    public static Content createAdminContent(
            ContentType type,
            String title,
            String description,
            String thumbnailUrl
    ) {
        Content content = new Content();

        content.type = type;
        content.title = title;
        content.description = description;
        content.thumbnailUrl = thumbnailUrl;
        content.provider = ContentSource.ADMIN;
        content.providerItemId = null;

        return content;
    }

    public void update(
            String title,
            String description,
            String thumbnailUrl,
            List<String> tags
    ) {
        this.title = title;
        this.description = description;

        if (thumbnailUrl != null) {
            this.thumbnailUrl = thumbnailUrl;
        }

        if (tags != null) {
            // 요청에 없는 기존 태그 삭제
            this.tags.removeIf(
                    contentTag -> !tags.contains(contentTag.getTag())
            );

            List<String> existingTags = this.tags.stream()
                    .map(ContentTag::getTag)
                    .toList();

            // 기존에 없는 태그만 추가
            tags.stream()
                    .filter(tag -> !existingTags.contains(tag))
                    .forEach(this::addTag);
        }
    }

    public void addTag(String tag) {
        ContentTag contentTag = new ContentTag(this, tag);
        tags.add(contentTag);
    }

}
