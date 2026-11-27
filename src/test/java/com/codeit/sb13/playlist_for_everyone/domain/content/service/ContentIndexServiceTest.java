package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentIndexBatch;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexReader;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexService;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentIndexService 테스트")
class ContentIndexServiceTest {

    @Mock
    private ContentIndexReader contentIndexReader;

    @Mock
    private ContentIndexWriter contentIndexWriter;

    @InjectMocks
    private ContentIndexService contentIndexService;

    @Test
    @DisplayName("마지막 콘텐츠 ID를 기준으로 여러 배치를 순차적으로 인덱싱한다.")
    void indexAll_multiplePages() {
        // given
        Pageable pageable = PageRequest.of(0, 500);

        List<ContentDocument> firstDocuments = List.of(createDocument("콘텐츠1"));
        List<ContentDocument> secondDocuments = List.of(createDocument("콘텐츠2"));

        UUID firstLastId = UUID.fromString(firstDocuments.get(0).getId());
        UUID secondLastId = UUID.fromString(secondDocuments.get(0).getId());

        ContentIndexBatch firstBatch = new ContentIndexBatch(
                firstDocuments, firstLastId, true
        );

        ContentIndexBatch secondBatch = new ContentIndexBatch(
                secondDocuments, secondLastId, false
        );

        given(contentIndexReader.readDocuments(null, pageable))
                .willReturn(firstBatch);
        given(contentIndexReader.readDocuments(firstLastId, pageable))
                .willReturn(secondBatch);

        // when
        contentIndexService.indexAll();

        // then
        InOrder inOrder = inOrder(
                contentIndexReader,
                contentIndexWriter
        );

        inOrder.verify(contentIndexReader).readDocuments(null, pageable);
        inOrder.verify(contentIndexWriter).saveAll(firstDocuments);
        inOrder.verify(contentIndexReader).readDocuments(firstLastId, pageable);
        inOrder.verify(contentIndexWriter).saveAll(secondDocuments);

        verifyNoMoreInteractions(contentIndexReader, contentIndexWriter);
    }

    @Test
    @DisplayName("조회된 콘텐츠가 없으면 인덱싱하지 않는다.")
    void indexAll_empty() {
        // given
        Pageable pageable = PageRequest.of(0, 500);

        ContentIndexBatch batch = new ContentIndexBatch(
                List.of(), null, false
        );

        given(contentIndexReader.readDocuments(null, pageable)).willReturn(batch);

        // when
        contentIndexService.indexAll();

        // then
        verify(contentIndexReader).readDocuments(null, pageable);
    }

    @Test
    @DisplayName("Elasticsearch 저장 실패 시 인덱싱을 중단하고 예외를 전파한다.")
    void indexAll_saveFailed() {
        // given
        Pageable pageable = PageRequest.of(0, 500);

        List<ContentDocument> documents = List.of(createDocument("콘텐츠1"));

        UUID lastId = UUID.fromString(documents.get(0).getId());

        ContentIndexBatch batch = new ContentIndexBatch(
                documents, lastId, true
        );

        given(contentIndexReader.readDocuments(null, pageable))
                .willReturn(batch);

        willThrow(new RuntimeException("Elasticsearch 저장 실패"))
                .given(contentIndexWriter)
                .saveAll(documents);

        // when & then
        assertThatThrownBy(() -> contentIndexService.indexAll())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Elasticsearch 저장 실패");

        verify(contentIndexReader).readDocuments(null, pageable);
        verify(contentIndexWriter).saveAll(documents);

        // 저장 실패 이후 다음 배치를 조회하지 않는지 검증
        verifyNoMoreInteractions(contentIndexReader, contentIndexWriter);
    }

    @Test
    @DisplayName("변경된 콘텐츠 문서를 일괄 저장한다.")
    void upsertAll_success() {
        // given
        List<UUID> contentIds = List.of(
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        List<ContentDocument> documents = List.of(
                createDocument("콘텐츠1"),
                createDocument("콘텐츠2")
        );

        given(contentIndexReader.readDocuments(contentIds)).willReturn(documents);

        // when
        contentIndexService.upsertAll(contentIds);

        // then
        InOrder inOrder = inOrder(contentIndexReader, contentIndexWriter);

        inOrder.verify(contentIndexReader).readDocuments(contentIds);
        inOrder.verify(contentIndexWriter).saveAll(documents);

        verifyNoMoreInteractions(contentIndexReader, contentIndexWriter);
    }

    @Test
    @DisplayName("콘텐츠 ID 목록이 비어 있으면 인덱싱하지 않는다.")
    void upsertAll_emptyIds() {
        // given
        List<UUID> contentIds = List.of();

        // when
        contentIndexService.upsertAll(contentIds);

        // then
        verifyNoInteractions(contentIndexReader, contentIndexWriter);
    }

    @Test
    @DisplayName("콘텐츠의 최신 정보를 조회하여 Elasticsearch에 저장한다.")
    void upsert_success() {
        // given
        ContentDocument document = createDocument("콘텐츠1");
        UUID contentId = UUID.fromString(document.getId());

        given(contentIndexReader.readDocument(contentId))
                .willReturn(Optional.of(document));

        // when
        contentIndexService.upsert(contentId);

        // then
        InOrder inOrder = inOrder(
                contentIndexReader,
                contentIndexWriter
        );

        inOrder.verify(contentIndexReader).readDocument(contentId);
        inOrder.verify(contentIndexWriter).save(document);

        verify(contentIndexWriter, never()).delete(any());
    }

    @Test
    @DisplayName("콘텐츠가 존재하지 않으면 Elasticsearch에 남아 있는 문서를 삭제한다.")
    void upsert_contentNotFound() {
        // given
        UUID contentId = UUID.randomUUID();

        given(contentIndexReader.readDocument(contentId))
                .willReturn(Optional.empty());

        // when
        contentIndexService.upsert(contentId);

        // then
        verify(contentIndexReader).readDocument(contentId);
        verify(contentIndexWriter).delete(contentId);
    }

    @Test
    @DisplayName("콘텐츠 ID에 해당하는 Elasticsearch 문서를 삭제한다.")
    void delete_success() {
        // given
        UUID contentId = UUID.randomUUID();

        // when
        contentIndexService.delete(contentId);

        // then
        verify(contentIndexWriter).delete(contentId);
        verifyNoInteractions(contentIndexReader);
    }

    @Test
    @DisplayName("배치 내 콘텐츠가 모두 삭제되어도 다음 배치의 인덱싱을 계속한다.")
    void indexAll_emptyBatchWithNext() {
        // given
        Pageable pageable = PageRequest.of(0, 500);
        UUID firstLastId = UUID.randomUUID();

        List<ContentDocument> documents = List.of(createDocument("콘텐츠2"));

        UUID secondLastId = UUID.fromString(documents.get(0).getId());

        ContentIndexBatch firstBatch = new ContentIndexBatch(
                List.of(), firstLastId, true
        );

        ContentIndexBatch secondBatch = new ContentIndexBatch(
                documents, secondLastId, false
        );

        given(contentIndexReader.readDocuments(null, pageable))
                .willReturn(firstBatch);
        given(contentIndexReader.readDocuments(firstLastId, pageable))
                .willReturn(secondBatch);

        // when
        contentIndexService.indexAll();

        // then
        InOrder inOrder = inOrder(
                contentIndexReader,
                contentIndexWriter
        );

        inOrder.verify(contentIndexReader).readDocuments(null, pageable);
        inOrder.verify(contentIndexReader).readDocuments(firstLastId, pageable);
        inOrder.verify(contentIndexWriter).saveAll(documents);

        verify(contentIndexWriter, never()).saveAll(List.of());
        verifyNoMoreInteractions(contentIndexReader, contentIndexWriter);
    }

    private ContentDocument createDocument(String title) {
        return ContentDocument.builder()
                .id(UUID.randomUUID().toString())
                .type(ContentType.tvSeries)
                .title(title)
                .description("테스트 콘텐츠 설명")
                .tags(List.of("드라마", "로맨스"))
                .createdAt(LocalDateTime.now())
                .build();
    }

}
