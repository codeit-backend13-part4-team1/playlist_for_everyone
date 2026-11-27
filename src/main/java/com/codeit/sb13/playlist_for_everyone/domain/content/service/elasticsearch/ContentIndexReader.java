package com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentIndexBatch;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentDocumentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentIndexReader {

    private final ContentRepository contentRepository;
    private final ContentDocumentMapper contentDocumentMapper;

    /**
     * Elasticsearch 초기 색인에 필요한 콘텐츠 데이터를 배치 단위로 조회한다.
     *  *
     *  * Keyset 페이징을 적용하여 마지막 콘텐츠 ID를 기준으로 조회한다.
     *  * 컬렉션 연관관계 조회 시 메모리 페이징을 방지하기 위해
     *  * 콘텐츠 ID를 먼저 조회한 후 콘텐츠 및 태그를 일괄 조회한다.
     *  * DB 조회 트랜잭션 내부에서 ContentDocument로 변환하여 반환한다.
     */
    public ContentIndexBatch readDocuments(UUID lastId, Pageable pageable) {
        // 마지막 콘텐츠 ID를 기준으로 배치 조회
        Slice<UUID> ids = lastId == null
                ? contentRepository.findAllIds(pageable)
                : contentRepository.findAllIdsAfter(lastId, pageable);

        // 조회된 콘텐츠 ID가 없으면 빈 Slice 반환
        if (ids.isEmpty()) {
            return new ContentIndexBatch(List.of(), lastId, false);
        }

        List<UUID> contentIds = ids.getContent();

        // 다음 배치 조회에 사용할 마지막 콘텐츠 ID
        UUID nextLastId = contentIds.get(contentIds.size() - 1);

        // 콘텐츠 및 연관 데이터를 일괄 조회하여 N+1 문제 방지
        // 트랜잭션 내부에서 ContentDocument로 변환
        List<ContentDocument> documents = contentRepository.findAllByIds(contentIds)
        .stream()
                .map(contentDocumentMapper::toContentDocument)
                .toList();

        return new ContentIndexBatch(
                documents,
                nextLastId,
                ids.hasNext()
        );
    }

    /**
     * Elasticsearch 단건 인덱싱에 필요한 콘텐츠 데이터를 조회한다.
     *
     * 콘텐츠 및 연관 태그를 조회하고 트랜잭션 내부에서
     * ContentDocument로 변환하여 반환한다.
     */
    @Transactional(
            readOnly = true,
            propagation = Propagation.REQUIRES_NEW
    )
    public Optional<ContentDocument> readDocument(UUID contentId) {
        return contentRepository.findAllByIds(List.of(contentId))
                .stream()
                .findFirst()
                .map(contentDocumentMapper::toContentDocument);
    }

    /**
     * 변경된 콘텐츠의 일괄 인덱싱에 필요한 데이터를 조회한다.
     */
    @Transactional(
            readOnly = true,
            propagation = Propagation.REQUIRES_NEW
    )
    public List<ContentDocument> readDocuments(List<UUID> contentIds) {
        if (contentIds.isEmpty()) {
            return List.of();
        }

        return contentRepository.findAllByIds(contentIds)
                .stream()
                .map(contentDocumentMapper::toContentDocument)
                .toList();
    }

}
