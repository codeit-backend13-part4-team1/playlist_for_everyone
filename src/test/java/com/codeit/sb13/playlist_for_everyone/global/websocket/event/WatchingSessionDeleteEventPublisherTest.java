package com.codeit.sb13.playlist_for_everyone.global.websocket.event;

import com.codeit.sb13.playlist_for_everyone.global.config.RetryConfig;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionDeleteEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(classes = {
        RetryConfig.class,
        WatchingSessionDeleteEventPublisher.class,
        WatchingSessionDeleteEventPublisherTest.TestConfig.class
})
public class WatchingSessionDeleteEventPublisherTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        RedisTemplate<String, Object> redisTemplate() {
            return Mockito.mock(RedisTemplate.class);
        }
    }

    @Autowired
    private WatchingSessionDeleteEventPublisher publisher;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final String topic = "websocket:event:watching:delete";
    private final UUID contentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Mockito.reset(redisTemplate);
    }

    @Test
    @DisplayName("Redis 이벤트 발행에 성공하면 한 번만 호출한다")
    void publish_success() {
        // when
        publisher.publish(topic, contentId);

        // then
        verify(redisTemplate, times(1))
                .convertAndSend(topic, contentId.toString());
    }

    @Test
    @DisplayName("Redis 연결 실패 후 재시도하여 발행에 성공한다")
    void publish_retrySuccess() {
        // given
        when(redisTemplate.convertAndSend(
                topic, contentId.toString()
        ))
                .thenThrow(new RedisConnectionFailureException("Redis 연결 실패"))
                .thenReturn(1L);

        // when
        publisher.publish(topic, contentId);

        // then
        verify(redisTemplate, times(2))
                .convertAndSend(topic, contentId.toString());
    }

    @Test
    @DisplayName("Redis 연결이 계속 실패하면 최대 3회 재시도한다")
    void publish_retryExhausted() {
        // given
        when(redisTemplate.convertAndSend(
                topic, contentId.toString()
        ))
                .thenThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        // when & then
        assertThatThrownBy(() -> publisher.publish(topic, contentId))
                .isInstanceOf(RedisConnectionFailureException.class);

        verify(redisTemplate, times(3))
                .convertAndSend(topic, contentId.toString());
    }

}
