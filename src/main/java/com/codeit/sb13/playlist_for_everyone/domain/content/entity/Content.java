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

    public void addTag(String tag) {
        ContentTag contentTag = new ContentTag(this, tag);
        this.tags.add(contentTag);
    }

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
        content.tags = new ArrayList<>();

        return content;
    }

}
