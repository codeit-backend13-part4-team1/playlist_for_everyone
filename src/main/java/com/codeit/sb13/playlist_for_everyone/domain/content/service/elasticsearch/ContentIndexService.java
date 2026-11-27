package com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentIndexBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentIndexService {

    // 한 번에 조회하고 인덱싱할 콘텐츠 개수
    private static final int BATCH_SIZE = 500;

    private final ContentIndexReader contentIndexReader;
    private final ContentIndexWriter contentIndexWriter;
    private final ContentIndexLock contentIndexLock;

    /**
     * 기존 PostgreSQL 콘텐츠 데이터를 Elasticsearch에 일괄 인덱싱한다.
     *
     * Keyset 페이징을 적용하여 콘텐츠 삭제로 인한 조회 누락을 방지한다.
     * 배치 단위로 분산 락을 획득하여 다른 색인 작업과의 충돌을 방지하며,
     * DB 조회 트랜잭션이 종료된 후 Elasticsearch에 저장한다.
     */
    public void indexAll() {
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        UUID lastId = null;
        long totalIndexed = 0;

        while (true) {
            UUID currentLastId = lastId;

            // 배치 단위로 분산 락을 획득하여 조회 및 저장을 수행한다.
            ContentIndexBatch batch = contentIndexLock.execute(() -> {
                ContentIndexBatch currentBatch =
                        contentIndexReader.readDocuments(currentLastId, pageable);

                if (!currentBatch.documents().isEmpty()) {
                    contentIndexWriter.saveAll(currentBatch.documents());
                }

                return currentBatch;
            });

            totalIndexed += batch.documents().size();

            if (!batch.documents().isEmpty()) {
                log.info("콘텐츠 인덱싱 진행: {}건", totalIndexed);
            }

            // 다음 배치가 없으면 인덱싱 종료
            if (!batch.hasNext()) {
                break;
            }

            // 다음 배치 조회 기준 갱신
            lastId = batch.lastId();
        }

        log.info("콘텐츠 인덱싱 완료: 총 {}건", totalIndexed);
    }

    /**
     * 변경된 콘텐츠 문서를 일괄 생성하거나 갱신한다.
     */
    public void upsertAll(List<UUID> contentIds) {
        if (contentIds.isEmpty()) {
            return;
        }

        contentIndexLock.execute(() -> {
            List<ContentDocument> documents =
                    contentIndexReader.readDocuments(contentIds);

            if (!documents.isEmpty()) {
                contentIndexWriter.saveAll(documents);
            }
        });
    }

    /**
     * 콘텐츠의 최신 정보를 Elasticsearch에 반영한다.
     */
    public void upsert(UUID contentId) {
        contentIndexLock.execute(() -> {
            contentIndexReader.readDocument(contentId)
                    .ifPresentOrElse(
                            contentIndexWriter::save,
                            () -> contentIndexWriter.delete(contentId)
                    );
        });
    }

    /**
     * Elasticsearch에서 콘텐츠 문서를 삭제한다.
     */
    public void delete(UUID contentId) {
        contentIndexLock.execute(() -> {
            contentIndexWriter.delete(contentId);
        });
    }

}
