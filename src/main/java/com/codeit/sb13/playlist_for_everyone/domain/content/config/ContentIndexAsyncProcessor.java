package com.codeit.sb13.playlist_for_everyone.domain.content.config;

import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentSearchCacheEvictService;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 콘텐츠 색인 작업을 전용 실행기에서 비동기로 처리한다.
 *
 * 색인 성공 여부와 관계없이 검색 캐시를 무효화한다.
 * 작업 제출 실패는 호출한 이벤트 리스너에서 처리한다.
 */
@Component
@RequiredArgsConstructor
public class ContentIndexAsyncProcessor {

    private final ContentIndexService contentIndexService;
    private final ContentSearchCacheEvictService cacheEvictService;

    @Async("contentIndexTaskExecutor")
    public void upsert(UUID contentId) {
        try {
            contentIndexService.upsert(contentId);
        } finally {
            cacheEvictService.clearSearchCache();
        }
    }

    @Async("contentIndexTaskExecutor")
    public void delete(UUID contentId) {
        try {
            contentIndexService.delete(contentId);
        } finally {
            cacheEvictService.clearSearchCache();
        }
    }

    @Async("contentIndexTaskExecutor")
    public void upsertAll(List<UUID> contentIds) {
        try {
            contentIndexService.upsertAll(contentIds);
        } finally {
            cacheEvictService.clearSearchCache();
        }
    }
}