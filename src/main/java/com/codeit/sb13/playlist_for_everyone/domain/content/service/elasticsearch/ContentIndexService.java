package com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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

    /**
     * 기존 PostgreSQL 콘텐츠 데이터를 Elasticsearch에 일괄 인덱싱한다.
     *
     * 대량의 데이터를 한 번에 조회하지 않도록 배치 단위로 처리하며,
     * DB 조회 트랜잭션이 종료된 후 Elasticsearch에 저장한다.
     */
    public void indexAll() {
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        long totalIndexed = 0;

        while (true) {
            // 배치 단위로 콘텐츠 및 태그를 조회하고 Document로 변환
            Slice<ContentDocument> documents = contentIndexReader.readDocuments(pageable);

            // 인덱싱할 콘텐츠가 없으면 종료
            if (documents.isEmpty()) {
                break;
            }

            // DB 조회 트랜잭션 외부에서 Elasticsearch 일괄 저장
            contentIndexWriter.saveAll(documents.getContent());
            // 누적 인덱싱 건수 집계 및 진행 상황 기록
            totalIndexed += documents.getNumberOfElements();

            log.info("콘텐츠 인덱싱 진행: {}건", totalIndexed);

            // 다음 페이지가 없으면 인덱싱 종료
            if (!documents.hasNext()) {
                break;
            }

            pageable = documents.nextPageable();
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
    }

    /**
     * 콘텐츠의 최신 정보를 Elasticsearch에 반영한다.
     */
    public void upsert(UUID contentId) {
        contentIndexReader.readDocument(contentId)
                .ifPresentOrElse(
                        contentIndexWriter::save,
                        () -> contentIndexWriter.delete(contentId)
                );
    }

    /**
     * Elasticsearch에서 콘텐츠 문서를 삭제한다.
     */
    public void delete(UUID contentId) {
        contentIndexWriter.delete(contentId);
    }

}
