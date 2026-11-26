package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContentSearchCacheEvictService {

    private final CacheManager cacheManager;

    /**
     * 콘텐츠 검색 목록 캐시를 무효화한다.
     */
    public void clearSearchCache() {
        Cache cache = cacheManager.getCache(CacheConfig.CONTENT_SEARCH);

        if (cache != null) {
            cache.clear();
        }
    }

}
