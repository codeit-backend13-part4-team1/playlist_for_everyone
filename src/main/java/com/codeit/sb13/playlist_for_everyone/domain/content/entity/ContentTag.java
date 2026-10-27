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

    @Column(nullable = false, length = 50)
    private String tag;

    public ContentTag(String tag) {
        this.tag = tag;
    }

}
