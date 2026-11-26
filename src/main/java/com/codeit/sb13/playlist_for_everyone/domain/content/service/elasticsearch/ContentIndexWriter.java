package com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.elasticsearch.ContentDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentIndexWriter {

    private final ContentDocumentRepository contentDocumentRepository;
    private final ElasticsearchOperations operations;

    /**
     * 콘텐츠 문서를 생성하거나 갱신한다.
     */
    public void save(ContentDocument document) {
        contentDocumentRepository.save(document);
        refresh();
    }

    /**
     * 콘텐츠 문서를 일괄 저장한다.
     */
    public void saveAll(List<ContentDocument> documents) {
        contentDocumentRepository.saveAll(documents);
        refresh();
    }

    /**
     * 콘텐츠 ID에 해당하는 문서를 삭제한다.
     */
    public void delete(UUID contentId) {
        contentDocumentRepository.deleteById(contentId.toString());
        refresh();
    }

    /**
     * 인덱스 변경 사항을 검색 결과에 반영한다.
     */
    private void refresh() {
        operations.indexOps(ContentDocument.class).refresh();
    }

}
