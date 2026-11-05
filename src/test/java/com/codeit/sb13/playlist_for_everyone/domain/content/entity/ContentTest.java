package com.codeit.sb13.playlist_for_everyone.domain.content.entity;

import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("Content 엔터티")
public class ContentTest {

    @Test
    @DisplayName("관리자 콘텐츠 생성 시 필드와 파일 정보가 올바르게 설정된다.")
    void createAdminContent_success() {
        // given
        FileInfo fileInfo = mock(FileInfo.class);

        // when
        Content content = Content.createAdminContent(
                ContentType.movie,
                "인셉션",
                "영화 설명",
                "thumbnail-url",
                fileInfo
        );

        // then
        assertThat(content.getType()).isEqualTo(ContentType.movie);
        assertThat(content.getTitle()).isEqualTo("인셉션");
        assertThat(content.getDescription()).isEqualTo("영화 설명");
        assertThat(content.getThumbnailUrl()).isEqualTo("thumbnail-url");
        assertThat(content.getFileInfo()).isSameAs(fileInfo);
        assertThat(content.getProvider()).isEqualTo(ContentSource.ADMIN);
        assertThat(content.getProviderItemId()).isNull();
    }

    @Test
    @DisplayName("수집한 영화에는 TMDB 출처와 외부 ID가 설정된다.")
    void createCollectedContent_success() {
        Content content = Content.createCollectedContent(
                ContentType.movie,
                "테스트 영화",
                "영화 설명",
                "https://example.com/poster.jpg",
                ContentSource.TMDB,
                "550"
        );

        assertThat(content.getType()).isEqualTo(ContentType.movie);
        assertThat(content.getProvider()).isEqualTo(ContentSource.TMDB);
        assertThat(content.getProviderItemId()).isEqualTo("550");
    }

    @Test
    @DisplayName("관리자 출처로는 수집 콘텐츠를 생성할 수 없다.")
    void createCollectedContent_rejectsAdmin() {
        assertThatThrownBy(() -> Content.createCollectedContent(
                ContentType.movie, "테스트 영화", "영화 설명", "thumbnail-url",
                ContentSource.ADMIN, "550"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("외부 ID가 없으면 수집 콘텐츠를 생성할 수 없다.")
    void createCollectedContent_rejectsMissingExternalId(String externalId) {
        assertThatThrownBy(() -> Content.createCollectedContent(
                ContentType.movie, "테스트 영화", "영화 설명", "thumbnail-url",
                ContentSource.TMDB, externalId
        )).isInstanceOf(IllegalArgumentException.class);
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
                List.of("액션", "스릴러")
        );

        // then
        assertThat(content.getTitle()).isEqualTo("수정된 인셉션");
        assertThat(content.getDescription()).isEqualTo("수정된 영화 설명");

        assertThat(content.getTags())
                .extracting(ContentTag::getTag)
                .containsExactlyInAnyOrder("액션", "스릴러");

        // 기존 태그 객체가 그대로 유지되는지 확인
        assertThat(content.getTags()).contains(existingTag);
    }

    @Test
    @DisplayName("썸네일 수정 시 URL과 FileInfo가 함께 변경된다.")
    void update_thumbnail() {
        // given
        FileInfo oldFileInfo = mock(FileInfo.class);
        FileInfo newFileInfo = mock(FileInfo.class);

        String oldThumbnailUrl = "thumbnail-url";
        String newThumbnailUrl = "new-thumbnail-url";

        Content content = Content.createAdminContent(
                ContentType.movie,
                "인셉션",
                "영화 설명",
                oldThumbnailUrl,
                oldFileInfo
        );

        // when
        content.updateThumbnail(newThumbnailUrl, newFileInfo);

        // then
        assertThat(content.getThumbnailUrl()).isEqualTo(newThumbnailUrl);
        assertThat(content.getFileInfo()).isSameAs(newFileInfo);
    }

}
