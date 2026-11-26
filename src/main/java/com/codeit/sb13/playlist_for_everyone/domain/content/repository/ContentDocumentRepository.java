package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ContentDocumentRepository
        extends ElasticsearchRepository<ContentDocument, String>, ContentDocumentSearchRepository {
}
