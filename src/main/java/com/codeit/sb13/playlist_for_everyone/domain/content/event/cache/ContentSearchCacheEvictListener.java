package com.codeit.sb13.playlist_for_everyone.domain.content.event.cache;

import com.codeit.sb13.playlist_for_everyone.domain.content.event.ContentCollectionFinishedEvent;
import com.codeit.sb13.playlist_for_everyone.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ContentSearchCacheEvictListener {

    private final CacheManager cacheManager;

    /**
     * 콘텐츠 또는 리뷰 변경 시 DB 트랜잭션 커밋 이후 검색 목록 캐시 무효화
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evict(ContentSearchCacheEvictEvent event) {
        clearSearchCache();
    }

    /** 배치에서 커밋된 신규 콘텐츠가 있으면 캐시 삭제 */
    @EventListener
    public void evictAfterCollection(ContentCollectionFinishedEvent event) {
        if (event.insertedCount() > 0) {
            clearSearchCache();
        }
    }

    private void clearSearchCache() {
        Cache cache = cacheManager.getCache(CacheConfig.CONTENT_SEARCH);

        if (cache != null) {
            cache.clear();
        }
    }


}
