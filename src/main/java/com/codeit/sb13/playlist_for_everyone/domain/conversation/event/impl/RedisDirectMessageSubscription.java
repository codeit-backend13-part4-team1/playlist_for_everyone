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

    @Override
    @PostConstruct
    public void start() {
       redisBrokerRegister.register(
               subscriber,
               properties.redis().channel());
    }

    @Override
    @PreDestroy
    public void stop() {
        redisBrokerRegister.unregister(
                subscriber,
                properties.redis().channel());
    }
}
