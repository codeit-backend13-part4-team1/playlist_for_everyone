package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.elasticsearch.test.autoconfigure.DataElasticsearchTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

    @AfterEach
    void tearDown() {
        contentDocumentRepository.deleteAll();
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

}
