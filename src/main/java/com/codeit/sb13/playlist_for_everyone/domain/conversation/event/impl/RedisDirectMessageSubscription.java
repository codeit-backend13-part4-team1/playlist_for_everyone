package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.config.DirectMessageTransportProperties;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageSubscription;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.MessageListener;

@RequiredArgsConstructor
public class RedisDirectMessageSubscription implements DirectMessageSubscription {

    private final RedisBrokerRegister redisBrokerRegister;
    private final MessageListener subscriber;
    private final DirectMessageTransportProperties properties;


    @Override
    public void start() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException();
    }
}
