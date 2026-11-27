package com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch;

import com.codeit.sb13.playlist_for_everyone.domain.content.config.ContentIndexAsyncProcessor;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.ContentCollectionFinishedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentSearchCacheEvictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentIndexEventListener {

    private final ContentIndexAsyncProcessor asyncProcessor;
    private final ContentSearchCacheEvictService cacheEvictService;

    /**
     * DB 커밋 이후 콘텐츠 문서를 생성하거나 갱신한다.
     * 작업 제출 실패 시 검색 캐시를 무효화한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUpsert(ContentIndexUpsertEvent event) {
        try {
            asyncProcessor.upsert(event.contentId());
        } catch (TaskRejectedException e) {
            cacheEvictService.clearSearchCache();
        }
    }

    /**
     * DB 커밋 이후 콘텐츠 문서를 삭제한다.
     * 작업 제출 실패 시 검색 캐시를 무효화한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDelete(ContentIndexDeleteEvent event) {
        try {
            asyncProcessor.delete(event.contentId());
        } catch (TaskRejectedException e) {
            log.error("콘텐츠 색인 삭제 작업 제출 실패. contentId={}",
                    event.contentId());

            cacheEvictService.clearSearchCache();
        }
    }

    /**
     * 콘텐츠 수집 완료 이후 변경된 콘텐츠를 일괄 인덱싱하고
     * 작업 제출 실패 시 검색 캐시를 무효화한다.
     */
    @EventListener
    public void handleCollection(ContentCollectionFinishedEvent event) {
        if (event.insertedCount() > 0 || event.updatedCount() > 0) {
            try {
                asyncProcessor.upsertAll(event.changedContentIds());
            } catch (TaskRejectedException e) {
                log.error("콘텐츠 일괄 색인 작업 제출 실패. contentIds={}",
                        event.changedContentIds());

                cacheEvictService.clearSearchCache();
            }
        }
    }

}
