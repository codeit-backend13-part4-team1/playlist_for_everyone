package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentIndexService {

    // 한 번에 조회하고 인덱싱할 콘텐츠 개수
    private static final int BATCH_SIZE = 500;

    private final ContentIndexReader contentIndexReader;
    private final ContentDocumentRepository contentDocumentRepository;

    /**
     * 기존 PostgreSQL 콘텐츠 데이터를 Elasticsearch에 일괄 인덱싱한다.
     *
     * 대량의 데이터를 한 번에 조회하지 않도록 배치 단위로 처리하며,
     * DB 조회 트랜잭션이 종료된 후 Elasticsearch에 저장한다.
     */
    public void indexAll() {

    }

}
