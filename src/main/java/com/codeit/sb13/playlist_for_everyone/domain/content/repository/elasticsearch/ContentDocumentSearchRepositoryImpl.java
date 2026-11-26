package com.codeit.sb13.playlist_for_everyone.domain.content.repository.elasticsearch;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ContentDocumentSearchRepositoryImpl implements ContentDocumentSearchRepository {

    private static final int BATCH_SIZE = 500;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 검색 조건에 일치하는 콘텐츠 ID를 조회한다.
     * 키워드, 콘텐츠 유형, 태그 조건을 동적으로 적용한다.
     */
    @Override
    public List<UUID> searchContentIds(ContentSearchCondition condition) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        keywordContains(boolQuery, condition.keywordLike());
        typeContains(boolQuery, condition.typeEqual());
        tagsInContains(boolQuery, condition.tagsIn());

        NativeQuery query = NativeQuery.builder()
                .withQuery(boolQuery.build()._toQuery())
                .withPageable(PageRequest.of(0, BATCH_SIZE))
                .build();

        List<UUID> contentIds = new ArrayList<>();

        // 검색 결과를 배치 단위로 조회하여 콘텐츠 ID 추출
        try (SearchHitsIterator<ContentDocument> hits =
                     elasticsearchOperations.searchForStream(
                             query, ContentDocument.class
                     )) {

            while (hits.hasNext()) {
                ContentDocument document = hits.next().getContent();
                contentIds.add(UUID.fromString(document.getId()));
            }
        }

        return contentIds;
    }

    // 제목 또는 설명을 대상으로 키워드 검색
    private void keywordContains(BoolQuery.Builder boolQuery, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }

        boolQuery.must(q -> q.multiMatch(m -> m
                .fields("title", "description")
                .query(keyword)
                .type(TextQueryType.Phrase)
        ));
    }

    // 콘텐츠 유형 필터링
    private void typeContains(BoolQuery.Builder boolQuery, ContentType type) {
        if (type == null) {
            return;
        }

        boolQuery.filter(q -> q.term(t -> t
                .field("type")
                .value(type.name())
        ));
    }

    // 요청한 태그 중 하나 이상을 가진 콘텐츠 검색
    private void tagsInContains(
            BoolQuery.Builder boolQuery,
            List<String> tags
    ) {
        if (tags == null || tags.isEmpty()) {
            return;
        }

        List<FieldValue> values = tags.stream()
                .map(FieldValue::of)
                .toList();

        boolQuery.filter(q -> q.terms(t -> t
                .field("tags")
                .terms(v -> v.value(values))
        ));
    }

}
