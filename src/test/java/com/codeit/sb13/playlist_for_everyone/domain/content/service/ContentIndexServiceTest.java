package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentIndexBatch;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexLock;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexReader;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexService;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private ContentIndexService contentIndexService;

    @BeforeEach
    void setUp() {
        ReentrantLock testLock = new ReentrantLock();

        lenient().when(redissonClient.getLock("everyone:lock:content:index"))
                .thenReturn(lock);

        // 실제 스레드 간 상호 배제 동작을 재현한다.
        lenient().doAnswer(invocation -> {
            testLock.lock();
            return null;
        }).when(lock).lock();

        lenient().doAnswer(invocation -> {
            testLock.unlock();
            return null;
        }).when(lock).unlock();

        ContentIndexLock contentIndexLock =
                new ContentIndexLock(redissonClient);

        contentIndexService = new ContentIndexService(
                contentIndexReader,
                contentIndexWriter,
                contentIndexLock
        );
    }

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

    @Test
    @DisplayName("동시 수정 시 이전 스냅샷이 최신 색인 문서를 덮어쓰지 않는다.")
    void upsert_concurrentUpdate() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();

        ContentDocument oldDocument = createDocument(contentId, "이전 제목");
        ContentDocument newDocument = createDocument(contentId, "최신 제목");

        AtomicReference<ContentDocument> dbDocument = new AtomicReference<>(oldDocument);
        AtomicReference<ContentDocument> indexedDocument = new AtomicReference<>();

        CountDownLatch oldSaveStarted = new CountDownLatch(1);
        CountDownLatch releaseOldSave = new CountDownLatch(1);
        CountDownLatch newSaveCompleted = new CountDownLatch(1);

        given(contentIndexReader.readDocument(contentId))
                .willAnswer(invocation ->
                        Optional.of(dbDocument.get()));

        doAnswer(invocation -> {
            oldSaveStarted.countDown();
            assertThat(releaseOldSave.await(5, TimeUnit.SECONDS)).isTrue();
            indexedDocument.set(oldDocument);
            return null;
        }).when(contentIndexWriter).save(oldDocument);

        doAnswer(invocation -> {
            indexedDocument.set(newDocument);
            newSaveCompleted.countDown();
            return null;
        }).when(contentIndexWriter).save(newDocument);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // when
            Future<?> first = executor.submit(
                    () -> contentIndexService.upsert(contentId)
            );

            assertThat(oldSaveStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // 첫 번째 색인이 지연되는 동안 DB의 최신 정보가 변경된다.
            dbDocument.set(newDocument);

            Future<?> second = executor.submit(
                    () -> contentIndexService.upsert(contentId)
            );

            // 분산 락이 없다면 최신 문서가 먼저 저장된다.
            newSaveCompleted.await(1, TimeUnit.SECONDS);

            releaseOldSave.countDown();

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            // then
            assertThat(indexedDocument.get()).isEqualTo(newDocument);

        } finally {
            releaseOldSave.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("수정과 삭제가 동시에 발생해도 삭제된 색인 문서가 다시 생성되지 않는다.")
    void upsert_concurrentDelete() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();
        ContentDocument document = createDocument(contentId, "콘텐츠1");

        AtomicReference<ContentDocument> indexedDocument = new AtomicReference<>(document);

        CountDownLatch saveStarted = new CountDownLatch(1);
        CountDownLatch releaseSave = new CountDownLatch(1);
        CountDownLatch deleteCompleted = new CountDownLatch(1);

        given(contentIndexReader.readDocument(contentId)).willReturn(Optional.of(document));

        doAnswer(invocation -> {
            saveStarted.countDown();
            assertThat(releaseSave.await(5, TimeUnit.SECONDS)).isTrue();
            indexedDocument.set(document);
            return null;
        }).when(contentIndexWriter).save(document);

        doAnswer(invocation -> {
            indexedDocument.set(null);
            deleteCompleted.countDown();
            return null;
        }).when(contentIndexWriter).delete(contentId);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // when
            Future<?> upsertFuture = executor.submit(
                    () -> contentIndexService.upsert(contentId)
            );

            assertThat(saveStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> deleteFuture = executor.submit(
                    () -> contentIndexService.delete(contentId)
            );

            // 분산 락이 없다면 이전 스냅샷 저장보다 삭제가 먼저 완료된다.
            deleteCompleted.await(1, TimeUnit.SECONDS);

            releaseSave.countDown();

            upsertFuture.get(5, TimeUnit.SECONDS);
            deleteFuture.get(5, TimeUnit.SECONDS);

            // then
            assertThat(indexedDocument.get()).isNull();

        } finally {
            releaseSave.countDown();
            executor.shutdownNow();
        }
    }

    private ContentDocument createDocument(String title) {
        return createDocument(UUID.randomUUID(), title);
    }

    private ContentDocument createDocument(UUID contentId, String title) {
        return ContentDocument.builder()
                .id(contentId.toString())
                .type(ContentType.tvSeries)
                .title(title)
                .description("테스트 콘텐츠 설명")
                .tags(List.of("드라마", "로맨스"))
                .createdAt(LocalDateTime.now())
                .build();
    }

}
