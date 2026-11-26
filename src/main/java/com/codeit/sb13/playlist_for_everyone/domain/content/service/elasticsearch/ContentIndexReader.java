package com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentDocumentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
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
     * Elasticsearch 인덱싱에 필요한 콘텐츠 데이터를 배치 단위로 조회한다.
     *
     * 컬렉션 연관관계 조회 시 메모리 페이징을 방지하기 위해
     * 콘텐츠 ID를 먼저 조회한 후 콘텐츠 및 태그를 일괄 조회한다.
     * DB 조회 트랜잭션 내부에서 ContentDocument로 변환하여 반환한다.
     */
    public Slice<ContentDocument> readDocuments(Pageable pageable) {
        // 배치 단위로 콘텐츠 ID 조회
        Slice<UUID> ids = contentRepository.findAllIds(pageable);

        // 조회된 콘텐츠 ID가 없으면 빈 Slice 반환
        if (ids.isEmpty()) {
            return new SliceImpl<>(List.of(), pageable, false);
        }

        // 콘텐츠 및 연관 데이터를 일괄 조회하여 N+1 문제 방지
        // 트랜잭션 내부에서 ContentDocument로 변환
        List<ContentDocument> documents = contentRepository.findAllByIds(ids.getContent())
        .stream()
                .map(contentDocumentMapper::toContentDocument)
                .toList();

        // 기존 페이지 정보와 다음 페이지 존재 여부를 유지하여 반환
        return new SliceImpl<>(
                documents,
                pageable,
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
            propagation = Propagation.REQUIRED
    )
    public Optional<ContentDocument> readDocument(UUID contentId) {
        return contentRepository.findAllByIds(List.of(contentId))
                .stream()
                .findFirst()
                .map(contentDocumentMapper::toContentDocument);
    }

}
