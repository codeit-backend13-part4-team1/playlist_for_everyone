package com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch;

import com.codeit.sb13.playlist_for_everyone.domain.content.event.ContentCollectionFinishedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentSearchCacheEvictService;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ContentIndexEventListener {

    private final ContentIndexService contentIndexService;
    private final ContentSearchCacheEvictService cacheEvictService;

    /**
     * DB 커밋 이후 콘텐츠 문서를 생성하거나 갱신한다.
     * 색인 성공 여부와 관계없이 검색 캐시를 무효화한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUpsert(ContentIndexUpsertEvent event) {
        try {
            contentIndexService.upsert(event.contentId());
        } finally {
            cacheEvictService.clearSearchCache();
        }
    }

    /**
     * DB 커밋 이후 콘텐츠 문서를 삭제한다.
     * 색인 성공 여부와 관계없이 검색 캐시를 무효화한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDelete(ContentIndexDeleteEvent event) {
        try {
            contentIndexService.delete(event.contentId());
        } finally {
            cacheEvictService.clearSearchCache();
        }
    }

    /**
     * 콘텐츠 수집 완료 이후 변경된 콘텐츠를 일괄 인덱싱하고
     * 색인 성공 여부와 관계없이 검색 캐시를 무효화한다.
     */
    @EventListener
    public void handleCollection(ContentCollectionFinishedEvent event) {
        if (event.insertedCount() > 0 || event.updatedCount() > 0) {
            try {
                contentIndexService.upsertAll(event.changedContentIds());
            } finally {
                cacheEvictService.clearSearchCache();
            }
        }
    }

}
