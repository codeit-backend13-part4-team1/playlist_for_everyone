package com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch;

import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ContentIndexEventListener {

    private final ContentIndexService contentIndexService;

    /**
     * DB 커밋 이후 콘텐츠 문서를 생성하거나 갱신한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUpsert(ContentIndexUpsertEvent event) {
        contentIndexService.upsert(event.contentId());
    }

    /**
     * DB 커밋 이후 콘텐츠 문서를 삭제한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDelete(ContentIndexDeleteEvent event) {
        contentIndexService.delete(event.contentId());
    }

}
