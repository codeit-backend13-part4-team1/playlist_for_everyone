package com.codeit.sb13.playlist_for_everyone.domain.content.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContentDocumentMapper 테스트")
public class ContentDocumentMapperTest {

    private ContentDocumentMapper mapper = new ContentDocumentMapper();

    @Test
    @DisplayName("콘텐츠 엔터티를 ContentDocument로 반환한다.")
    void toDocument() {
        // given
        UUID contentId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 26, 10, 0);

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                null
        );

        ReflectionTestUtils.setField(content, "id", contentId);
        ReflectionTestUtils.setField(content, "createdAt", createdAt);

        content.addTag("드라마");
        content.addTag("로맨스");

        // when
        ContentDocument document = mapper.toContentDocument(content);

        // then
        assertThat(document.getId()).isEqualTo(contentId.toString());
        assertThat(document.getType()).isEqualTo(ContentType.tvSeries);
        assertThat(document.getTitle()).isEqualTo("너말고다른연애");
        assertThat(document.getDescription()).isEqualTo("KBS 드라마 입니다.");
        assertThat(document.getTags())
                .containsExactly("드라마", "로맨스");
        assertThat(document.getCreatedAt()).isEqualTo(createdAt);
    }

}
