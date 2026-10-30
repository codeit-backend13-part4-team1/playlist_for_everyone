package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.service.RedisBrokerRegister;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseTransportProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.MessageListener;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis SSE 구독 관리")
class RedisSseSubscriptionTest {

    @Mock
    private RedisBrokerRegister redisBrokerRegister;

    @Mock
    private MessageListener subscriber;

    private SseSubscription subscription;

    @BeforeEach
    void setUp(){
        SseTransportProperties properties = new SseTransportProperties("redis",
                new SseTransportProperties.Redis("test:sse:events"));

        subscription = new RedisSseSubscription(redisBrokerRegister, subscriber, properties);
    }

    @Test
    @DisplayName("구독을 시작하면 설정된 채널에 SSE 수신 리스너를 등록한다.")
    void registerSubscriberToConfiguredChannelOnStart(){
        subscription.start();
        verify(redisBrokerRegister).register(subscriber, "test:sse:events");
    }

}