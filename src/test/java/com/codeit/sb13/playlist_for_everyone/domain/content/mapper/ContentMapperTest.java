package com.codeit.sb13.playlist_for_everyone.domain.content.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContentMapper 테스트")
public class ContentMapperTest {

    private final ContentMapper contentMapper = new ContentMapper();

    @Test
    @DisplayName("콘텐츠 엔터티를 DTO로 반환한다.")
    void toDto_success() {
        // given
        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                null
        );
        content.addTag("드라마");
        content.addTag("로맨스");

        // when
        ContentDto dto = contentMapper.toDto(
                content,
                "thumbnail-url",
                4.5,
                10,
                20L
        );

        // then
        assertThat(dto.type()).isEqualTo(ContentType.tvSeries);
        assertThat(dto.title()).isEqualTo("너말고다른연애");
        assertThat(dto.description()).isEqualTo("KBS 드라마 입니다.");
        assertThat(content.getThumbnailUrl()).isNull();
        assertThat(dto.thumbnailUrl()).isEqualTo("thumbnail-url");

        assertThat(dto.tags())
                .containsExactly("드라마", "로맨스");

        assertThat(dto.averageRating()).isEqualTo(4.5);
        assertThat(dto.reviewCount()).isEqualTo(10);
        assertThat(dto.watcherCount()).isEqualTo(20L);
    }

    @Test
    @DisplayName("태그가 없을 때 빈 리스트를 반환한다.")
    void toDto_withoutTags() {
        // given
        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                null
        );

        // when
        ContentDto dto = contentMapper.toDto(
                content,
                "thumbnail-url",
                4.5,
                10,
                20L
        );

        // then
        assertThat(dto.tags()).isEmpty();
    }

    @Test
    @DisplayName("콘텐츠 엔터티를 ContentSummary로 변환한다")
    void toSummary_success() {
        // given
        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url"
        );
        content.addTag("드라마");
        content.addTag("로맨스");

        // when
        ContentSummary summary = contentMapper.toSummary(
                content,
                "thumbnail-url",
                4.5,
                10
        );

        // then
        assertThat(summary.contentId()).isEqualTo(content.getId());
        assertThat(summary.type()).isEqualTo(ContentType.tvSeries);
        assertThat(summary.title()).isEqualTo("너말고다른연애");
        assertThat(summary.description()).isEqualTo("KBS 드라마 입니다.");
        assertThat(summary.thumbnailUrl()).isEqualTo("thumbnail-url");
        assertThat(summary.tags()).containsExactly("드라마", "로맨스");
        assertThat(summary.averageRating()).isEqualTo(4.5);
        assertThat(summary.reviewCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("기존 DTO의 썸네일 URL만 변경한다.")
    void withThumbnailUrl_success() {
        // given
        UUID contentId = UUID.randomUUID();
        String presignedUrl = "https://example.com/thumbnail.jpg";

        ContentDto dto = new ContentDto(
                contentId,
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                null,
                List.of("드라마", "로맨스"),
                4.5,
                10,
                20L
        );

        // when
        ContentDto result = contentMapper.withThumbnailUrl(dto, presignedUrl);

        // then
        assertThat(result)
                .usingRecursiveComparison()
                .ignoringFields("thumbnailUrl")
                .isEqualTo(dto);

        assertThat(result.thumbnailUrl()).isEqualTo(presignedUrl);
        assertThat(dto.thumbnailUrl()).isNull();
    }

}
