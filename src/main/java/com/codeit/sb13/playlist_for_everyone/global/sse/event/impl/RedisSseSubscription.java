package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.service.RedisBrokerRegister;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseTransportProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseSubscription;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * 컨텍스트 시작·종료에 맞춰 SSE 리스너를 전용 Redis 채널에 등록·해제한다.
 * 공용 컨테이너는 WebSocket 등 다른 리스너도 사용하므로 여기서 컨테이너 자체를 중지하지 않는다.
 *
 * <p>subscription-enabled=false이면 이 구독 관리 빈만 생성하지 않는다.
 * SSE 발행자·수신자 빈이나 다른 기능의 Redis 사용까지 비활성화하는 설정은 아니다.
 */
@Component
@ConditionalOnProperty(
        prefix = "everyone.sse",
        name = "transport",
        havingValue = "redis"
)
@ConditionalOnProperty(
        prefix = "everyone.sse",
        name = "subscription-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisSseSubscription implements SseSubscription {

    private final RedisBrokerRegister redisBrokerRegister;
    private final MessageListener subscriber;
    private final SseTransportProperties properties;

    // 공용 MessageListener 타입의 다른 빈과 구분하여 SSE 수신 리스너를 명시적으로 선택한다.
    public RedisSseSubscription(
            RedisBrokerRegister redisBrokerRegister,
            @Qualifier("redisSseEventSubscriber")
            MessageListener subscriber,
            SseTransportProperties properties) {
        this.redisBrokerRegister = redisBrokerRegister;
        this.subscriber = subscriber;
        this.properties = properties;
    }

    @Override
    @PostConstruct
    public void start() {
        redisBrokerRegister.register(
                subscriber,
                properties.redis().channel()
        );

    }

    @Override
    @PreDestroy
    public void stop() {
        redisBrokerRegister.unregister(
                subscriber,
                properties.redis().channel()
        );
    }
}
