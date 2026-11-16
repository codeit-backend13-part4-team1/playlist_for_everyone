package com.codeit.sb13.playlist_for_everyone.domain.content.event;

import com.codeit.sb13.playlist_for_everyone.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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
        Cache cache = cacheManager.getCache(CacheConfig.CONTENT_SEARCH);

        if (cache != null) {
            cache.clear();
        }
    }

}
