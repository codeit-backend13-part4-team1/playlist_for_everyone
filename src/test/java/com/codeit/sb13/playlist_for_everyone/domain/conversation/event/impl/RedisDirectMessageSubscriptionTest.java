package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.config.DirectMessageTransportProperties;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageSubscription;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.MessageListener;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis DM 구독 관리")
class RedisDirectMessageSubscriptionTest {

    private static final String CHANNEL = "test:dm:events";

    @Mock
    private RedisBrokerRegister redisBrokerRegister;

    @Mock
    private MessageListener subscriber;

    private DirectMessageSubscription subscription;

    @BeforeEach
    void setUp(){
        DirectMessageTransportProperties properties = new DirectMessageTransportProperties(
                "redis",
                new DirectMessageTransportProperties.Redis(CHANNEL)
        );
        subscription = new RedisDirectMessageSubscription(
                redisBrokerRegister,
                subscriber,
                properties
        );
    }

    @Test
    @DisplayName("구독을 시작하면 설정된 Redis채널에 DM 수신 리스너를 한 번 등록한다.")
    void start_registersSubscriberToConfiguredChannel(){
        subscription.start();
        verify(redisBrokerRegister).register(subscriber, CHANNEL);
        verifyNoMoreInteractions(redisBrokerRegister);
    }

    @Test
    @DisplayName("구독을 종료하면 등록했던 Redis 채널에서 DM 수신 리스너를 한 번 해제한다.")
    void stop_unregistersSubscriberFromConfiguredChannel(){
        subscription.start();
        subscription.stop();

        verify(redisBrokerRegister).register(subscriber, CHANNEL);
        verify(redisBrokerRegister).unregister(subscriber, CHANNEL);
        verifyNoMoreInteractions(redisBrokerRegister);
    }

}