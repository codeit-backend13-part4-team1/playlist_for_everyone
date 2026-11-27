package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
    private TestEntityManager em;

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

    private User createAuthor(int number) {
        User author = User.builder()
                .email("test" + number + "@example.com")
                .name("테스트 사용자" + number)
                .password("encodedPassword")
                .role(Role.USER)
                .build();

        return em.persistFlushFind(author);
    }

    private Review createReview(Content content, User author, String text, double rating) {
        return Review.builder()
                .content(content)
                .author(author)
                .text(text)
                .rating(rating)
                .build();
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
    @DisplayName("콘텐츠 단건 조회 시 평균 평점과 리뷰 수를 함께 반환한다.")
    void findDetailById_withReviews() {
        // given
        // 콘텐츠 저장
        Content content = createContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url"
        );
        Content savedContent = contentRepository.save(content);

        // 사용자 저장
        User author1 = createAuthor(1);
        User author2 = createAuthor(2);

        // 리뷰 저장
        Review review1 = createReview(content, author1, "재밌어요!", 4.5);
        Review review2 = createReview(content, author2, "정말 좋아요!", 5.0);

        em.persist(review1);
        em.persist(review2);

        // when
        ContentDetailProjection result = contentRepository
                .findDetailById(savedContent.getId())
                .orElseThrow();

        // then
        assertThat(result.getContent().getId())
                .isEqualTo(savedContent.getId());
        assertThat(result.getAverageRating())
                .isEqualTo(4.75);
        assertThat(result.getReviewCount())
                .isEqualTo(2L);

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

    @Test
    @DisplayName("콘텐츠 저장 시 FileInfo 연관관계가 정상적으로 저장된다.")
    void save_withFileInfo() {
        // given
        FileInfo fileInfo = FileInfo.createS3File(
                "contents/test-thumbnail.jpg",
                "thumbnail.jpg",
                "image/jpeg",
                1024L
        );
        em.persist(fileInfo);

        Content content = Content.createAdminContent(
                ContentType.movie,
                "인셉션",
                "영화 설명",
                "thumbnail-url",
                fileInfo
        );

        // when
        Content savedContent = contentRepository.save(content);

        em.flush();
        em.clear();

        // then
        Content found = contentRepository.findById(savedContent.getId())
                .orElseThrow();

        assertThat(found.getFileInfo()).isNotNull();
        assertThat(found.getFileInfo().getId()).isEqualTo(fileInfo.getId());
    }

    @Test
    @DisplayName("콘텐츠 ID를 오름차순으로 페이징 조회한다.")
    void findAllIds_success() {
        // given
        Content content1 = createContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url"
        );
        Content content2 = createContent(
                ContentType.tvSeries,
                "굿파트너2",
                "장나라, 김혜윤 주연",
                "thumbnail-url2"
        );
        Content content3 = createContent(
                ContentType.movie,
                "호프",
                "최신 영화",
                "thumbnail-url3"
        );

        contentRepository.saveAllAndFlush(List.of(content1, content2, content3));

        Pageable pageable = PageRequest.of(0, 2);

        // when
        Slice<UUID> result = contentRepository.findAllIds(pageable);

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .isSortedAccordingTo(Comparator.comparing(UUID::toString));
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("마지막 페이지에서는 hasNext가 false를 반환한다.")
    void findAllIds_lastPage() {
        Content content1 = createContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url"
        );
        Content content2 = createContent(
                ContentType.tvSeries,
                "굿파트너2",
                "장나라, 김혜윤 주연",
                "thumbnail-url2"
        );
        Content content3 = createContent(
                ContentType.movie,
                "호프",
                "최신 영화",
                "thumbnail-url3"
        );

        contentRepository.saveAllAndFlush(List.of(content1, content2, content3));

        Pageable pageable = PageRequest.of(1, 2);

        // when
        Slice<UUID> result = contentRepository.findAllIds(pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("저장된 콘텐츠가 없으면 빈 Slice를 반환한다.")
    void findAllIds_empty() {
        // given
        Pageable pageable = PageRequest.of(0, 2);

        // when
        Slice<UUID> result = contentRepository.findAllIds(pageable);

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("이전 배치의 콘텐츠가 삭제되어도 다음 콘텐츠 ID를 누락 없이 조회한다.")
    void findAllIdsAfter_deletedContent() {
        // given
        contentRepository.saveAllAndFlush(List.of(
                Content.createAdminContent(
                        ContentType.tvSeries,
                        "콘텐츠1",
                        "테스트 설명1",
                        null
                ),
                Content.createAdminContent(
                        ContentType.tvSeries,
                        "콘텐츠2",
                        "테스트 설명2",
                        null
                ),
                Content.createAdminContent(
                        ContentType.tvSeries,
                        "콘텐츠3",
                        "테스트 설명3",
                        null
                )
        ));

        Pageable pageable = PageRequest.of(0, 2);

        // 삭제 전 전체 ID 확인
        List<UUID> allIds = contentRepository
                .findAllIds(PageRequest.of(0, 3))
                .getContent();

        assertThat(allIds).hasSize(3);

        // 첫 번째 배치 조회
        Slice<UUID> firstBatch = contentRepository.findAllIds(pageable);

        assertThat(firstBatch.getContent())
                .containsExactly(allIds.get(0), allIds.get(1));
        assertThat(firstBatch.hasNext()).isTrue();

        UUID lastId = firstBatch.getContent().get(1);

        // 이미 조회한 콘텐츠 삭제
        contentRepository.deleteById(allIds.get(0));
        contentRepository.flush();

        // when
        Slice<UUID> secondBatch =
                contentRepository.findAllIdsAfter(lastId, pageable);

        // then
        assertThat(secondBatch.getContent())
                .containsExactly(allIds.get(2));
        assertThat(secondBatch.hasNext()).isFalse();
    }

}
