package com.codeit.sb13.playlist_for_everyone.domain.content.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
                "thumbnail-url"
        );
        content.addTag("드라마");
        content.addTag("로맨스");

        // when
        ContentDto dto = contentMapper.toDto(
                content,
                4.5,
                10,
                20L
        );

        // then
        assertThat(dto.type()).isEqualTo(ContentType.tvSeries);
        assertThat(dto.title()).isEqualTo("너말고다른연애");
        assertThat(dto.description()).isEqualTo("KBS 드라마 입니다.");
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
                "thumbnail-url"
        );

        // when
        ContentDto dto = contentMapper.toDto(
                content,
                4.5,
                10,
                20L
        );

        // then
        assertThat(dto.tags()).isEmpty();
    }

}
