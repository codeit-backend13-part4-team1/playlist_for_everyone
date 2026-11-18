package com.codeit.sb13.playlist_for_everyone.domain.content.event;

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

@ExtendWith(MockitoExtension.class)
class ContentSearchCacheEvictListenerTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private ContentSearchCacheEvictListener listener;

    @Test
    @DisplayName("콘텐츠 변경 이벤트를 받으면 검색 캐시를 무효화한다.")
    void evict_success() {
        // given
        given(cacheManager.getCache(CacheConfig.CONTENT_SEARCH))
                .willReturn(cache);

        // when
        listener.evict(new ContentSearchCacheEvictEvent());

        // then
        verify(cache).clear();
    }

}
