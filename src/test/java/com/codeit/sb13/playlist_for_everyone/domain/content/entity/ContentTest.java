package com.codeit.sb13.playlist_for_everyone.domain.content.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Content 엔터티")
public class ContentTest {

    @Test
    @DisplayName("관리자 콘텐츠 생성 시 필드와 출처가 올바르게 설정된다.")
    void createAdminContent_success() {
        // when
        Content content = Content.createAdminContent(
                ContentType.movie,
                "인셉션",
                "영화 설명",
                "thumbnail-url"
        );

        // then
        assertThat(content.getType()).isEqualTo(ContentType.movie);
        assertThat(content.getTitle()).isEqualTo("인셉션");
        assertThat(content.getDescription()).isEqualTo("영화 설명");
        assertThat(content.getThumbnailUrl()).isEqualTo("thumbnail-url");
        assertThat(content.getProvider()).isEqualTo(ContentSource.ADMIN);
        assertThat(content.getProviderItemId()).isNull();
    }

    @Test
    @DisplayName("콘텐츠에 여러 태그를 추가하면 태그 목록에 모두 추가된다.")
    void addTag_success() {
        // given
        Content content = Content.createAdminContent(
                ContentType.movie,
                "인셉션",
                "영화 설명",
                "thumbnail-url"
        );

        // when
        content.addTag("액션");
        content.addTag("SF");
        content.addTag("스릴러");

        // then
        assertThat(content.getTags())
                .hasSize(3)
                .extracting(ContentTag::getTag)
                .containsExactly("액션", "SF", "스릴러");
    }

    @Test
    @DisplayName("태그 없이도 관리자 콘텐츠를 생성할 수 있다.")
    void createAdminContent_withoutTags() {
        // when
        Content content = Content.createAdminContent(
                ContentType.movie,
                "인셉션",
                "영화 설명",
                "thumbnail-url"
        );

        // then
        assertThat(content.getTags()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("콘텐츠 수정 시 기존 태그는 유지하고 변경된 태그만 삭제 및 추가된다.")
    void update_tags_success() {
        // given
        Content content = Content.createAdminContent(
                ContentType.movie,
                "인셉션",
                "영화 설명",
                "thumbnail-url"
        );

        content.addTag("액션");
        content.addTag("SF");

        ContentTag existingTag = content.getTags().stream()
                .filter(tag -> tag.getTag().equals("액션"))
                .findFirst()
                .orElseThrow();

        // when
        content.update(
                "수정된 인셉션",
                "수정된 영화 설명",
                "new-thumbnail-url",
                List.of("액션", "스릴러")
        );

        // then
        assertThat(content.getTitle()).isEqualTo("수정된 인셉션");
        assertThat(content.getDescription()).isEqualTo("수정된 영화 설명");
        assertThat(content.getThumbnailUrl()).isEqualTo("new-thumbnail-url");

        assertThat(content.getTags())
                .extracting(ContentTag::getTag)
                .containsExactlyInAnyOrder("액션", "스릴러");

        // 기존 태그 객체가 그대로 유지되는지 확인
        assertThat(content.getTags()).contains(existingTag);
    }

}
