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

@Component
@ConditionalOnProperty(
        prefix = "everyone.sse",
        name = "transport",
        havingValue = "redis"
)
public class RedisSseSubscription implements SseSubscription {

    private final RedisBrokerRegister redisBrokerRegister;
    private final MessageListener subscriber;
    private final SseTransportProperties properties;

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

    }
}
