package com.codeit.sb13.playlist_for_everyone.domain.conversation.config;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessagePublisher;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl.RedisDirectMessagePublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("DM 전송 설정")
class DirectMessageTransportConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    DirectMessageTransportConfig.class,
                    RedisDirectMessagePublisher.class
            ).withBean(StringRedisTemplate.class,
                    () -> mock(StringRedisTemplate.class))
            .withBean(ObjectMapper.class,
                    () -> JsonMapper.builder().build());

    @Test
    @DisplayName("Redis 전송 설정을 바인딩하고 Redis 발행기를 공통 인터페이스로 조회한다.")
    void registersRedisPublisherWithBoundProperties(){
        contextRunner.withPropertyValues(
                "everyone.dm.websocket.transport=redis",
                "everyone.dm.websocket.redis.channel=test:dm:events"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DirectMessageTransportProperties.class)
                    .hasSingleBean(DirectMessagePublisher.class);

            DirectMessageTransportProperties properties = context.getBean(DirectMessageTransportProperties.class);
            assertThat(properties.transport()).isEqualTo("redis");
            assertThat(properties.redis()).isNotNull();
            assertThat(properties.redis().channel()).isEqualTo("test:dm:events");

            DirectMessagePublisher publisher = context.getBean(DirectMessagePublisher.class);
            assertThat(publisher).isInstanceOf(RedisDirectMessagePublisher.class);
        });
    }
}