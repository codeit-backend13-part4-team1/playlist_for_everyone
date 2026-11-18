package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.config.DirectMessageTransportProperties;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageSubscription;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis DM 채널 구독을 애플리케이션 시작·종료에 연결한다.
 *
 * <p>전송 방식이 redis이고 자동 구독이 활성화되면 등록한다. 공용 RedisBrokerRegister를 재사용하되
 * DM 전용 리스너·채널을 지정하므로 콘텐츠 채팅이나 SSE의 구독과 구분된다.
 * 구독을 꺼도 Publisher 빈과 다른 Redis 기능은 유지된다.
 */
@Component
@ConditionalOnProperty(
        prefix = "everyone.dm.websocket",
        name = "transport",
        havingValue = "redis"
)
@ConditionalOnProperty(
        prefix = "everyone.dm.websocket",
        name = "subscription-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisDirectMessageSubscription implements DirectMessageSubscription {

    private final RedisBrokerRegister redisBrokerRegister;
    private final MessageListener subscriber;
    private final DirectMessageTransportProperties properties;

    public RedisDirectMessageSubscription(
            RedisBrokerRegister redisBrokerRegister,
            @Qualifier("redisDirectMessageSubscriber")
            MessageListener subscriber,
            DirectMessageTransportProperties properties) {
        this.redisBrokerRegister = redisBrokerRegister;
        this.subscriber = subscriber;
        this.properties = properties;
    }

    /** 빈 초기화 시 등록한다. 채널 구독은 서버 단위이며 사용자별 STOMP 구독 수와는 다르다. */
    @Override
    @PostConstruct
    public void start() {
       redisBrokerRegister.register(
               subscriber,
               properties.redis().channel());
    }

    /** 이 리스너의 구독만 해제하며 공용 리스너 컨테이너 전체를 종료하지 않는다. */
    @Override
    @PreDestroy
    public void stop() {
        redisBrokerRegister.unregister(
                subscriber,
                properties.redis().channel());
    }
}
