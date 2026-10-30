package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import com.codeit.sb13.playlist_for_everyone.global.config.SseTransportConfig;
import com.codeit.sb13.playlist_for_everyone.global.service.RedisBrokerRegister;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseSubscription;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.impl.RedisSseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.impl.RedisSseEventSubscriber;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.impl.RedisSseSubscription;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Redis SSE 전송 구성")
public class RedisSseTransportConfigTest {
    private final RedisBrokerRegister redisBrokerRegister= mock(RedisBrokerRegister.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SseTransportConfig.class,
                    RedisSseEventPublisher.class,
                    RedisSseEventSubscriber.class,
                    RedisSseSubscription.class
                    )
            .withPropertyValues(
                    "everyone.sse.transport=redis",
                    "everyone.sse.redis.channel=test:sse:events"
            )
            .withBean(StringRedisTemplate.class,
                    () -> mock(StringRedisTemplate.class))
            .withBean(ObjectMapper.class,
                    () ->JsonMapper.builder().build())
            .withBean(SseDeliveryService.class,
                    () -> mock(SseDeliveryService.class))
            .withBean(RedisBrokerRegister.class,
                    ()->redisBrokerRegister);

    @Test
    @DisplayName("Redis 전송 설정이면 발행자와 수신자 및 구독 관리 빈이 연결된다.")
    void wiresRedisTransportBeans(){
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SseEventPublisher.class);
            assertThat(context).hasSingleBean(SseSubscription.class);
            assertThat(context).hasSingleBean(RedisSseEventSubscriber.class);
            MessageListener subscriber = context.getBean(RedisSseEventSubscriber.class);
            verify(redisBrokerRegister)
                    .register(subscriber, "test:sse:events");

        });
    }
}
