package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.config.DirectMessageTransportConfig;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageSubscription;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.MessageListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("Redis DM 구독 생명주기")
public class RedisDirectMessageSubscriptionLifecycleTest {

    private static final String CHANNEL = "test:dm:events";
    private final RedisBrokerRegister redisBrokerRegister = mock(RedisBrokerRegister.class);
    private final MessageListener subscriber = mock(MessageListener.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    DirectMessageTransportConfig.class,
                    RedisDirectMessageSubscription.class
            )
            .withPropertyValues(
                    "everyone.dm.websocket.transport=redis",
                    "everyone.dm.websocket.subscription-enabled=true",
                    "everyone.dm.websocket.redis.channel=" + CHANNEL
            )
            .withBean(RedisBrokerRegister.class,
                    () -> redisBrokerRegister)
            .withBean("redisDirectMessageSubscriber",
                    MessageListener.class,
                    ()-> subscriber
            )
            .withBean("redisSseEventSubscriber",
                    MessageListener.class,
                    ()-> mock(MessageListener.class));


    @Test
    @DisplayName("컨텍스트가 시작되면 여러 리스너 중 DM 리스너를 선택해 설정된 채널에 자동 등록한다.")
    void registerDmSubscriberWhenContextStarts(){
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DirectMessageSubscription.class);
            verify(redisBrokerRegister).register(subscriber, CHANNEL);
            verifyNoMoreInteractions(redisBrokerRegister);
        });
    }

    @Test
    @DisplayName("컨텍스트가 종료되면 등록했던 DM 리스너를 설정된 채널에서 자동 해제한다.")
    void unregisterDmSubscriberWhenContextCloses(){
        contextRunner.run(context ->{
            assertThat(context).hasNotFailed();
            verify(redisBrokerRegister).register(subscriber, CHANNEL);
            verify(redisBrokerRegister, never()).unregister(subscriber, CHANNEL);
        });
        verify(redisBrokerRegister).unregister(subscriber, CHANNEL);
        verifyNoMoreInteractions(redisBrokerRegister);
    }

    @Test
    @DisplayName("자동 구독을 비활성화하면 구독 관리 빈을 생성하지 않고 등록과 해제를 호출하지 않는다.")
    void doesNotSubscribeWhenSubscriptionIsDisabled(){
        contextRunner
                .withPropertyValues("everyone.dm.websocket.subscription-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DirectMessageSubscription.class);
                });
        verifyNoInteractions(redisBrokerRegister);
    }


}
