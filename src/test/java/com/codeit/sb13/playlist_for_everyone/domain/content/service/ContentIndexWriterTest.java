package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.elasticsearch.ContentDocumentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("콘텐츠 인덱스 저장 및 삭제 테스트")
class ContentIndexWriterTest {

    @Mock
    private ContentDocumentRepository contentDocumentRepository;

    @Mock
    private ElasticsearchOperations operations;

    @Mock
    private IndexOperations indexOperations;

    @InjectMocks
    private ContentIndexWriter contentIndexWriter;

    @BeforeEach
    void setUp() {
        given(operations.indexOps(ContentDocument.class))
                .willReturn(indexOperations);
    }

    @Test
    @DisplayName("콘텐츠 문서를 저장하고 인덱스를 갱신한다.")
    void save_success() {
        // given
        ContentDocument document = createDocument("콘텐츠1");

        // when
        contentIndexWriter.save(document);

        // then
        InOrder inOrder = inOrder(
                contentDocumentRepository, operations, indexOperations
        );

        inOrder.verify(contentDocumentRepository).save(document);
        inOrder.verify(operations).indexOps(ContentDocument.class);
        inOrder.verify(indexOperations).refresh();
    }

    @Test
    @DisplayName("콘텐츠 문서를 일괄 저장하고 인덱스를 갱신한다.")
    void saveAll_success() {
        // given
        List<ContentDocument> documents = List.of(
                createDocument("콘텐츠1"),
                createDocument("콘텐츠2")
        );

        // when
        contentIndexWriter.saveAll(documents);

        // then
        InOrder inOrder = inOrder(
                contentDocumentRepository, operations, indexOperations
        );

        inOrder.verify(contentDocumentRepository).saveAll(documents);
        inOrder.verify(operations).indexOps(ContentDocument.class);
        inOrder.verify(indexOperations).refresh();
    }

    @Test
    @DisplayName("콘텐츠 문서를 삭제하고 인덱스를 갱신한다.")
    void delete_success() {
        // given
        UUID contentId = UUID.randomUUID();

        // when
        contentIndexWriter.delete(contentId);

        // then
        InOrder inOrder = inOrder(
                contentDocumentRepository, operations, indexOperations
        );

        inOrder.verify(contentDocumentRepository)
                .deleteById(contentId.toString());
        inOrder.verify(operations).indexOps(ContentDocument.class);
        inOrder.verify(indexOperations).refresh();
    }

    private ContentDocument createDocument(String title) {
        return ContentDocument.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .build();
    }

}
