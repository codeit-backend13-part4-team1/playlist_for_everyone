package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.CollectedContentData;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, QueryDslConfig.class, ContentCollectionService.class})
@Testcontainers
class ContentCollectionServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private ContentCollectionService service;

    @Autowired
    private EntityManager em;

    @Test
    void 수집한_영화와_태그를_함께_저장한다() {
        var data = new CollectedContentData(
                ContentType.movie, "테스트 영화", "영화 설명",
                "https://example.com/poster.jpg", ContentSource.TMDB, "550",
                List.of("액션", " 액션 ", "", "드라마")
        );

        Content saved = service.save(data);
        em.flush();
        em.clear();

        Content found = em.find(Content.class, saved.getId());

        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("테스트 영화");
        assertThat(found.getProvider()).isEqualTo(ContentSource.TMDB);
        assertThat(found.getProviderItemId()).isEqualTo("550");
        assertThat(found.getTags())
                .extracting(ContentTag::getTag)
                .containsExactlyInAnyOrder("액션", "드라마");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 제목이_없으면_수집한_콘텐츠를_저장할_수_없다(String title) {
        var data = new CollectedContentData(
                ContentType.movie, title, "영화 설명",
                "https://example.com/poster.jpg", ContentSource.TMDB, "550",
                List.of("액션")
        );

        assertThatThrownBy(() -> service.save(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수집한 콘텐츠에는 제목이 필요합니다.");
    }

    @Test
    void 썸네일이_없는_경기도_정보와_태그를_저장한다() {
        var data = new CollectedContentData(
                ContentType.sport, "West Ham vs Tottenham", "경기 설명",
                null, ContentSource.THE_SPORTS_DB, "441425",
                List.of("Soccer", "English Premier League")
        );

        Content saved = service.save(data);
        em.flush();
        em.clear();

        Content found = em.find(Content.class, saved.getId());

        assertThat(found).isNotNull();
        assertThat(found.getThumbnailUrl()).isNull();
        assertThat(found.getTitle()).isEqualTo("West Ham vs Tottenham");
        assertThat(found.getType()).isEqualTo(ContentType.sport);
        assertThat(found.getProvider()).isEqualTo(ContentSource.THE_SPORTS_DB);
        assertThat(found.getProviderItemId()).isEqualTo("441425");
        assertThat(found.getTags())
                .extracting(ContentTag::getTag)
                .containsExactlyInAnyOrder("Soccer", "English Premier League");
    }

    @Test
    void 스포츠_응답을_변환해서_콘텐츠와_태그를_저장한다() {
        var event = new SportsDbEventResponse(
                "441425",
                "West Ham vs Tottenham",
                "Soccer",
                "English Premier League",
                "2014-2015",
                "2014-08-16",
                null
        );

        var data = new SportsDbContentMapper().fromEvent(event);

        Content saved = service.save(data);
        em.flush();
        em.clear();

        Content found = em.find(Content.class, saved.getId());

        assertThat(found).isNotNull();
        assertThat(found.getType()).isEqualTo(ContentType.sport);
        assertThat(found.getTitle()).isEqualTo("West Ham vs Tottenham");
        assertThat(found.getDescription())
                .isEqualTo("English Premier League / 2014-2015 / 2014-08-16");
        assertThat(found.getThumbnailUrl()).isNull();
        assertThat(found.getProvider()).isEqualTo(ContentSource.THE_SPORTS_DB);
        assertThat(found.getProviderItemId()).isEqualTo("441425");
        assertThat(found.getTags())
                .extracting(ContentTag::getTag)
                .containsExactlyInAnyOrder("Soccer", "English Premier League");
    }

    @Test
    void 제목이_100자를_넘으면_저장할_수_없다() {
        var data = new CollectedContentData(
                ContentType.movie, "가".repeat(101), "영화 설명",
                "https://example.com/poster.jpg", ContentSource.TMDB, "550",
                List.of("액션")
        );

        assertThatThrownBy(() -> service.save(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수집한 콘텐츠 제목은 100자 이하여야 합니다.");
    }

    @Test
    void 태그가_50자를_넘으면_저장할_수_없다() {
        var data = new CollectedContentData(
                ContentType.movie, "테스트 영화", "영화 설명",
                "https://example.com/poster.jpg", ContentSource.TMDB, "550",
                List.of("가".repeat(51))
        );

        assertThatThrownBy(() -> service.save(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수집한 콘텐츠 태그는 50자 이하여야 합니다.");
    }
}