package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.elasticsearch.ContentDocumentRepository;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.elasticsearch.test.autoconfigure.DataElasticsearchTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataElasticsearchTest
@ActiveProfiles("test")
@DisplayName("콘텐츠 Elasticsearch Repository 테스트")
public class ContentDocumentRepositoryTest {

    @Container
    @ServiceConnection
    static final ElasticsearchContainer elasticsearch =
            new ElasticsearchContainer(
                    "docker.elastic.co/elasticsearch/elasticsearch:9.2.0"
            )
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("xpack.security.http.ssl.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");


    @Autowired
    private ContentDocumentRepository contentDocumentRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    private void saveDocuments(ContentDocument... documents) {
        contentDocumentRepository.saveAll(List.of(documents));
        elasticsearchOperations.indexOps(ContentDocument.class).refresh();
    }

    @AfterEach
    void tearDown() {
        contentDocumentRepository.deleteAll();
        elasticsearchOperations.indexOps(ContentDocument.class).refresh();
    }

    @Test
    @DisplayName("콘텐츠 문서를 Elasticsearch에 저장하고 조회한다.")
    void saveAndFindById() {
        // given
        String contentId = UUID.randomUUID().toString();

        ContentDocument document = ContentDocument.builder()
                .id(contentId)
                .type(ContentType.tvSeries)
                .title("굿파트너2")
                .description("장나라, 김혜윤 주연")
                .tags(List.of("법정", "이혼"))
                .createdAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS))
                .build();

        // when
        contentDocumentRepository.save(document);

        // then
        ContentDocument result = contentDocumentRepository.findById(contentId)
                .orElseThrow();

        assertThat(result.getId()).isEqualTo(contentId);
        assertThat(result.getType()).isEqualTo(document.getType());
        assertThat(result.getTitle()).isEqualTo(document.getTitle());
        assertThat(result.getDescription()).isEqualTo(document.getDescription());
        assertThat(result.getTags()).containsExactlyElementsOf(document.getTags());
        assertThat(result.getCreatedAt()).isEqualTo(document.getCreatedAt());
    }

    @Test
    @DisplayName("제목에 키워드가 일치하는 콘텐츠를 조회한다.")
    void searchContentIds_keywordInTitle() {
        // given
        ContentDocument document1 = createDocument(
                ContentType.tvSeries,
                "너 말고 다른 연애",
                "KBS 드라마",
                List.of("로맨스")
        );

        ContentDocument document2 = createDocument(
                ContentType.tvSeries,
                "굿파트너",
                "SBS 드라마",
                List.of("법정")
        );
        saveDocuments(document1, document2);

        ContentSearchCondition condition = createCondition("연애", null, null);

        // when
        List<UUID> result = contentDocumentRepository.searchContentIds(condition);

        // then
        assertThat(result).containsExactly(UUID.fromString(document1.getId()));
    }

    @Test
    @DisplayName("설명에 키워드가 일치하는 콘텐츠를 조회한다.")
    void searchContentIds_keywordInDescription() {
        // given
        ContentDocument document1 = createDocument(
                ContentType.tvSeries,
                "굿파트너",
                "장나라 주연의 법정 드라마",
                List.of("드라마")
        );

        ContentDocument document2 = createDocument(
                ContentType.tvSeries,
                "너 말고 다른 연애",
                "KBS 로맨스 드라마",
                List.of("로맨스")
        );
        saveDocuments(document1, document2);

        ContentSearchCondition condition = createCondition("법정", null, null);

        // when
        List<UUID> result = contentDocumentRepository.searchContentIds(condition);

        // then
        assertThat(result).containsExactly(UUID.fromString(document1.getId()));
    }

    @Test
    @DisplayName("요청한 유형과 일치하는 콘텐츠를 조회한다.")
    void searchContentIds_typeEqual() {
        // given
        ContentDocument document1 = createDocument(
                ContentType.tvSeries,
                "굿파트너",
                "법정 드라마",
                List.of("법정")
        );

        ContentDocument document2 = createDocument(
                ContentType.movie,
                "인터스텔라",
                "우주 탐험 영화",
                List.of("SF")
        );
        saveDocuments(document1, document2);

        ContentSearchCondition condition = createCondition(null, ContentType.tvSeries, null);

        // when
        List<UUID> result = contentDocumentRepository.searchContentIds(condition);

        // then
        assertThat(result)
                .containsExactly(UUID.fromString(document1.getId()));
    }

    @Test
    @DisplayName("요청한 태그 중 하나 이상 일치하는 콘텐츠를 조회한다.")
    void searchContentIds_tagsIn() {
        // given
        ContentDocument document1 = createDocument(
                ContentType.tvSeries,
                "굿파트너",
                "SBS 드라마",
                List.of("법정", "이혼")
        );

        ContentDocument document2 = createDocument(
                ContentType.tvSeries,
                "너 말고 다른 연애",
                "KBS 드라마",
                List.of("로맨스")
        );

        ContentDocument document3 = createDocument(
                ContentType.movie,
                "인터스텔라",
                "우주 탐험 영화",
                List.of("SF")
        );
        saveDocuments(document1, document2, document3);

        ContentSearchCondition condition = createCondition(null, null, List.of("법정", "로맨스"));

        // when
        List<UUID> result = contentDocumentRepository.searchContentIds(condition);

        // then
        assertThat(result)
                .containsExactlyInAnyOrder(
                        UUID.fromString(document1.getId()),
                        UUID.fromString(document2.getId())
                );
    }

    @Test
    @DisplayName("키워드, 유형, 태그 조건을 모두 충족하는 콘텐츠를 조회한다.")
    void searchContentIds_multipleConditions() {
        // given
        ContentDocument document1 = createDocument(
                ContentType.tvSeries,
                "너 말고 다른 연애",
                "KBS 드라마",
                List.of("로맨스")
        );

        ContentDocument document2 = createDocument(
                ContentType.tvSeries,
                "굿파트너",
                "SBS 드라마",
                List.of("로맨스")
        );

        ContentDocument document3 = createDocument(
                ContentType.movie,
                "연애 이야기",
                "로맨스 영화",
                List.of("로맨스")
        );

        ContentDocument document4 = createDocument(
                ContentType.tvSeries,
                "연애의 시작",
                "코미디 드라마",
                List.of("코미디")
        );
        saveDocuments(document1, document2, document3, document4);

        ContentSearchCondition condition = createCondition(
                        "연애",
                        ContentType.tvSeries,
                        List.of("로맨스")
                );

        // when
        List<UUID> result = contentDocumentRepository.searchContentIds(condition);

        // then
        assertThat(result).containsExactly(UUID.fromString(document1.getId()));
    }

    @Test
    @DisplayName("검색 조건이 없으면 전체 콘텐츠를 조회한다.")
    void searchContentIds_noCondition() {
        // given
        ContentDocument document1 = createDocument(
                ContentType.tvSeries,
                "굿파트너",
                "SBS 드라마",
                List.of("법정")
        );

        ContentDocument document2 = createDocument(
                ContentType.movie,
                "인터스텔라",
                "우주 탐험 영화",
                List.of("SF")
        );
        saveDocuments(document1, document2);

        ContentSearchCondition condition = createCondition(null, null, null);

        // when
        List<UUID> result = contentDocumentRepository.searchContentIds(condition);

        // then
        assertThat(result)
                .containsExactlyInAnyOrder(
                        UUID.fromString(document1.getId()),
                        UUID.fromString(document2.getId())
                );
    }

    @Test
    @DisplayName("검색 조건에 일치하는 콘텐츠가 없으면 빈 목록을 반환한다.")
    void searchContentIds_noResult() {
        // given
        ContentDocument document = createDocument(
                ContentType.tvSeries,
                "굿파트너",
                "SBS 드라마",
                List.of("법정")
        );
        saveDocuments(document);

        ContentSearchCondition condition = createCondition("인터스텔라", null, null);

        // when
        List<UUID> result = contentDocumentRepository.searchContentIds(condition);

        // then
        assertThat(result).isEmpty();
    }

    private ContentDocument createDocument (
            ContentType type,
            String title,
            String description,
            List<String> tags
    ) {
        return ContentDocument.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .title(title)
                .description(description)
                .tags(tags)
                .createdAt(LocalDateTime.now()
                        .truncatedTo(ChronoUnit.MILLIS))
                .build();
    }

    private ContentSearchCondition createCondition (
            String keyword,
            ContentType type,
            List<String> tags
    ) {
        return new ContentSearchCondition(
                type,
                keyword,
                tags,
                null, // cursor
                null, // idAfter
                20,   // limit
                SortDirection.DESCENDING,
                "createdAt"
        );
    }

}
