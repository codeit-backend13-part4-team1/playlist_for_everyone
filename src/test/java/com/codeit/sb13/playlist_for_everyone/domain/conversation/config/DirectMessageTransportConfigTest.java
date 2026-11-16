package com.codeit.sb13.playlist_for_everyone.domain.conversation.config;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessagePublisher;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageSubscription;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl.RedisDirectMessagePublisher;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl.RedisDirectMessageSubscriber;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl.RedisDirectMessageSubscription;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("DM 전송 설정")
class DirectMessageTransportConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    DirectMessageTransportConfig.class,
                    RedisDirectMessagePublisher.class,
                    RedisDirectMessageSubscriber.class,
                    RedisDirectMessageSubscription.class
            ).withBean(StringRedisTemplate.class,
                    () -> mock(StringRedisTemplate.class))
            .withBean(ObjectMapper.class,
                    () -> JsonMapper.builder().build())
            .withBean(SimpMessagingTemplate.class,
                    () -> mock(SimpMessagingTemplate.class))
            .withBean(RedisBrokerRegister.class,
                    () -> mock(RedisBrokerRegister.class))

            ;

    @Test
    @DisplayName("Redis 설정을 바인딩하고 실제 DM 수신기를 설정된 채널에 등록한다.")
    void registersRedisTransportWithBoundProperties(){
        contextRunner.withPropertyValues(
                "everyone.dm.websocket.transport=redis",
                "everyone.dm.websocket.subscription-enabled=true",
                "everyone.dm.websocket.redis.channel=test:dm:events"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DirectMessageTransportProperties.class)
                    .hasSingleBean(DirectMessagePublisher.class)
                    .hasSingleBean(DirectMessageSubscription.class)
                    .hasSingleBean(RedisDirectMessageSubscriber.class);

            DirectMessageTransportProperties properties = context.getBean(DirectMessageTransportProperties.class);
            assertThat(properties.transport()).isEqualTo("redis");
            assertThat(properties.redis()).isNotNull();
            assertThat(properties.redis().channel()).isEqualTo("test:dm:events");

            DirectMessagePublisher publisher = context.getBean(DirectMessagePublisher.class);
            assertThat(publisher).isInstanceOf(RedisDirectMessagePublisher.class);
            MessageListener subscriber = context.getBean("redisDirectMessageSubscriber", MessageListener.class);
            RedisBrokerRegister brokerRegister = context.getBean(RedisBrokerRegister.class);
            verify(brokerRegister).register(subscriber, "test:dm:events");
            verifyNoMoreInteractions(brokerRegister);
        });
    }
}