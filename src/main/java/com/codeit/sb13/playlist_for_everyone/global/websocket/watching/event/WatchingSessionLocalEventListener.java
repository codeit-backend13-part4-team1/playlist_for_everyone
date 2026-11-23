package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "everyone.websocket.event.multiple", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class WatchingSessionLocalEventListener {

    private final WatchingSessionService watchingSessionService;


    @TransactionalEventListener(
            // 트랜잭션이 정상적으로 커밋된 후에 실행
            phase = TransactionPhase.AFTER_COMMIT,
            // 트랜잭션 없이 이벤트가 발행되어도 실행
            fallbackExecution = true
    )
    public void onContentsDelete(WatchingSessionContentDeleteEvent event){
        UUID contentId = Objects.requireNonNull(event.contentId());
        watchingSessionService.cleanUpWithContentId(contentId);
    }
}
