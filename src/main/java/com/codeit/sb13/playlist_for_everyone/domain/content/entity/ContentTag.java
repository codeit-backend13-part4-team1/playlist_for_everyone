package com.codeit.sb13.playlist_for_everyone.domain.content.entity;

import com.codeit.sb13.playlist_for_everyone.global.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "content_tags",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_content_tag",
                        columnNames = {"content_id", "tag"}
                )
        }
)
public class ContentTag extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Column(nullable = false, length = 50)
    private String tag;

    public ContentTag(Content content, String tag) {
        this.content = content;
        this.tag = tag;
    }

}
