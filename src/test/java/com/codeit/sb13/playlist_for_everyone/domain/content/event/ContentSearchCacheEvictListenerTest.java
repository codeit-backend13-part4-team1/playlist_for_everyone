package com.codeit.sb13.playlist_for_everyone.domain.content.event;

import com.codeit.sb13.playlist_for_everyone.global.config.CacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

    @Test
    void 배치에서_신규_저장한_콘텐츠가_있으면_캐시를_삭제한다() {
        given(cacheManager.getCache(CacheConfig.CONTENT_SEARCH))
                .willReturn(cache);

        new ApplicationContextRunner()
                .withBean(CacheManager.class, () -> cacheManager)
                .withUserConfiguration(ContentSearchCacheEvictListener.class)
                .run(context -> {
                    context.publishEvent(new ContentCollectionFinishedEvent(3L));

                    verify(cache).clear();
                });
    }

    @Test
    void 배치에서_신규_저장한_콘텐츠가_없으면_캐시를_삭제하지_않는다() {
        new ApplicationContextRunner()
                .withBean(CacheManager.class, () -> cacheManager)
                .withUserConfiguration(ContentSearchCacheEvictListener.class)
                .run(context -> {
                    context.publishEvent(new ContentCollectionFinishedEvent(0L));

                    verifyNoInteractions(cacheManager, cache);
                });
    }

}
