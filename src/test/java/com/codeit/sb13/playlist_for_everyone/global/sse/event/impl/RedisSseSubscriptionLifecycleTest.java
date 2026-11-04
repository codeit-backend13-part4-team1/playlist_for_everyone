package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.config.SseTransportConfig;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseSubscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.MessageListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("Redis SSE 구독 생명주기")
public class RedisSseSubscriptionLifecycleTest {
    private final RedisBrokerRegister redisBrokerRegister = mock(RedisBrokerRegister.class);

    private final MessageListener subscriber = mock(MessageListener.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SseTransportConfig.class,
                    RedisSseSubscription.class)
            .withPropertyValues("everyone.sse.transport=redis",
                    "everyone.sse.redis.channel=test:sse:events")
            .withBean(RedisBrokerRegister.class,
                    ()-> redisBrokerRegister)
            .withBean("redisSseEventSubscriber",
                    MessageListener.class,
                    () -> subscriber)
            .withBean("chatMessageListener",
                    MessageListener.class,
                    () -> mock(MessageListener.class));

    @Test
    @DisplayName("컨텍스트가 시작되면 SSE 수신 리스너를 설정된 채널에 자동 등록한다.")
    void registerSubscriberWhenContextStarts(){
        contextRunner.run(context ->{
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SseSubscription.class);

            verify(redisBrokerRegister).register(subscriber, "test:sse:events");
        });
    }

    @Test
    @DisplayName("컨텍스트가 종료되면 SSE 수신 리스너를 설정된 채널에서 자동 해제한다.")
    void unregistersSubscriberWhenContextCloses(){
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            verify(redisBrokerRegister, never())
                    .unregister(subscriber, "test:sse:events");
        });

        verify(redisBrokerRegister).unregister(subscriber, "test:sse:events");
    }

    @Test
    @DisplayName("자동 구독을 비활성화하면 구독 관리 빈을 생성하지 않고 채널을 구독하지 않는다.")
    void doesNotSubscribeWhenSubscriptionIsDisabled(){
        contextRunner.withPropertyValues("everyone.sse.subscription-enabled=false")
                .run(context ->{
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SseSubscription.class);
                });

        verifyNoInteractions(redisBrokerRegister);
    }
}
