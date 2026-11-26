package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.global.config.CacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ContentSearchCacheEvictServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private ContentSearchCacheEvictService cacheEvictService;

    @Test
    @DisplayName("콘텐츠 검색 캐시를 무효화한다.")
    void clearSearchCache_success() {
        // given
        given(cacheManager.getCache(CacheConfig.CONTENT_SEARCH))
                .willReturn(cache);

        // when
        cacheEvictService.clearSearchCache();

        // then
        verify(cache).clear();
    }

    @Test
    @DisplayName("검색 캐시가 존재하지 않으면 무효화하지 않는다.")
    void clearSearchCache_notFound() {
        // given
        given(cacheManager.getCache(CacheConfig.CONTENT_SEARCH))
                .willReturn(null);

        // when
        cacheEvictService.clearSearchCache();

        // then
        verify(cacheManager).getCache(CacheConfig.CONTENT_SEARCH);
        verifyNoInteractions(cache);
    }

}
