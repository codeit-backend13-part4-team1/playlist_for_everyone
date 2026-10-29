package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
        JpaAuditingConfig.class,
        QueryDslConfig.class
})
@Testcontainers
@DisplayName("Content 레포지토리")
public class ContentRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private EntityManager em;

    private Content createContent(
            ContentType type,
            String title,
            String description,
            String thumbnailUrl
    ) {
        return Content.createAdminContent(
                type,
                title,
                description,
                thumbnailUrl
        );
    }

    @Test
    @DisplayName("콘텐츠 저장 후 조회 시 필드가 정상적으로 반환된다.")
    void save_content_success() {
        // given
        Content content = createContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url"
        );

        // when
        Content savedContent = contentRepository.save(content);

        em.flush();
        em.clear();

        // then
        Content found = contentRepository.findById(savedContent.getId())
                .orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getType()).isEqualTo(ContentType.tvSeries);
        assertThat(found.getTitle()).isEqualTo("너말고다른연애");
        assertThat(found.getDescription()).isEqualTo("KBS 드라마 입니다.");
        assertThat(found.getThumbnailUrl()).isEqualTo("thumbnail-url");
        assertThat(found.getProvider()).isEqualTo(ContentSource.ADMIN);
        assertThat(found.getProviderItemId()).isNull();
        assertThat(found.getTags()).isEmpty();
    }

    @Test
    @DisplayName("콘텐츠 저장 후 조회 시 태그도 함께 반환된다.")
    void save_withTags() {
        // given
        Content content = createContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url"
        );
        content.addTag("드라마");
        content.addTag("로맨스");
        content.addTag("서강준");

        // when
        Content savedContent = contentRepository.save(content);

        em.flush();
        em.clear();

        // then
        Content found = contentRepository.findById(savedContent.getId())
                .orElseThrow();

        assertThat(found.getTags())
                .extracting(ContentTag::getTag)
                .containsExactlyInAnyOrder("로맨스", "드라마", "서강준");
    }

    @Test
    @DisplayName("태그 없이 저장한 콘텐츠는 조회 시 태그 목록이 비어 있다.")
    void save_withoutTags() {
        // given
        Content content = createContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url"
        );

        // when
        Content savedContent = contentRepository.save(content);

        em.flush();
        em.clear();

        // then
        Content found = contentRepository.findById(savedContent.getId())
                .orElseThrow();

        assertThat(found.getTags()).isEmpty();
    }

    @Test
    @DisplayName("콘텐츠 수정 시 변경된 필드와 태그가 DB에 반영된다.")
    void update_content_success() {
        // given
        Content content = createContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url"
        );

        content.addTag("드라마");
        content.addTag("로맨스");

        Content savedContent = contentRepository.save(content);

        em.flush();
        em.clear();

        // when
        Content found = contentRepository.findById(savedContent.getId())
                .orElseThrow();

        found.update(
                "수정된 제목",
                "수정된 설명",
                null,
                List.of("드라마", "액션")
        );

        em.flush();
        em.clear();

        // then
        Content updated = contentRepository.findById(savedContent.getId())
                .orElseThrow();

        assertThat(updated.getTitle()).isEqualTo("수정된 제목");
        assertThat(updated.getDescription()).isEqualTo("수정된 설명");
        assertThat(updated.getThumbnailUrl()).isEqualTo("thumbnail-url");

        assertThat(updated.getTags())
                .extracting(ContentTag::getTag)
                .containsExactlyInAnyOrder("드라마", "액션");
    }

}
