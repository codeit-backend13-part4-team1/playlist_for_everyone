package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
@DisplayName("ContentSearchRepository 테스트")
public class ContentSearchRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private ContentSearchRepository contentSearchRepository;

    @Autowired
    private TestEntityManager em;

    private Content createContent(ContentType contentType, String title, String description) {
         return Content.createAdminContent(
                contentType,
                title,
                description,
                null
        );
    }

    @BeforeEach
    void setUp() {
        em.persist(createContent(ContentType.movie, "영화시리즈1", "설명1"));
        em.persist(createContent(ContentType.movie, "영화시리즈2", "설명2"));
        em.persist(createContent(ContentType.sport, "스포츠1", "설명3"));
        em.persist(createContent(ContentType.sport, "스포츠드라마2", "설명4"));
        em.persist(createContent(ContentType.sport, "스포츠3", "설명5"));
        em.persist(createContent(ContentType.tvSeries, "드라마시리즈1", "설명6"));

        em.flush();
        em.clear();
    }


    @Test
    @DisplayName("콘텐츠 타입으로 검색할 수 있다")
    void searchContents_byType() {
        // given
        ContentSearchCondition condition = new ContentSearchCondition(
                ContentType.movie,
                null,
                null,
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        // when
        var result = contentSearchRepository.search(condition);

        // then
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(ContentSummary::type)
                .containsOnly(ContentType.movie);
    }

    @Test
    @DisplayName("태그로 검색하면 해당 태그를 가진 콘텐츠만 조회된다")
    void searchByTags() {
        // given
        ContentSearchCondition condition = new ContentSearchCondition(
                null,
                null,
                List.of("액션"),
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        // 태그를 가진 콘텐츠 저장
        Content movie = createContent(
                ContentType.movie,
                "액션영화",
                "액션영화 설명"
        );
        movie.addTag("액션");
        movie.addTag("스릴러");

        em.persist(movie);
        em.flush();
        em.clear();

        // when
        var result = contentSearchRepository.search(condition);

        // then
        assertThat(result).hasSize(1);
        assertThat(result)
                .extracting(ContentSummary::title)
                .containsExactly("액션영화");
        assertThat(result.get(0).tags())
                .contains("액션");
    }

    @Test
    @DisplayName("키워드 기준으로 검색한다.")
    void searchKeywords() {
        // given
        ContentSearchCondition condition = new ContentSearchCondition(
                null,
                "시리즈",
                null,
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        // when
        var result = contentSearchRepository.search(condition);

        // then
        assertThat(result).hasSize(3);
        assertThat(result)
                .extracting(ContentSummary::title)
                .containsExactlyInAnyOrder(
                        "영화시리즈1",
                        "영화시리즈2",
                        "드라마시리즈1"
                );
    }


}
