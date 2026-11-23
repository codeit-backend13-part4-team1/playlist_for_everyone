package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@ConditionalOnProperty(name = "everyone.websocket.event.multiple", havingValue = "true")
@RequiredArgsConstructor
public class WatchingSessionGlobalEventLister {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisBrokerRegister broker;
    private final WatchingSessionContentDeleteEventListener contentDelete;
    private final WatchingSessionDeleteEventPublisher publisher;

    @PostConstruct
    public void setup(){
        broker.register(contentDelete,contentDelete.topic());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContentsDelete(WatchingSessionContentDeleteEvent event) {
        publisher.publish(
                contentDelete.topic(),
                event.contentId()
        );
    }

}
