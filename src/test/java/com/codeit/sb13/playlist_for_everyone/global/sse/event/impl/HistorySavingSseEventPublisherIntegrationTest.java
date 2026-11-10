package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.config.SseEventPublisherConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.SseHistoryConfig;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryEntry;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryReadResult;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.impl.RedisSseHistoryStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {
                SseHistoryConfig.class,
                RedisSseHistoryStore.class,
                SseEventPublisherConfig.class
        },
        properties = {
                "everyone.sse.history.retention=10m",
                "everyone.sse.history.max-events-per-user=10",
                "everyone.sse.history.key-prefix=test:sse:publisher-history"
        }
)
@ImportAutoConfiguration({
        DataRedisAutoConfiguration.class,
        JacksonAutoConfiguration.class
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("SSE 발행과 Redis 이력 보존 통합 테스트")
public class HistorySavingSseEventPublisherIntegrationTest {

        @Container
        static final GenericContainer<?> redis = new GenericContainer<>(
                DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(6379);

        @DynamicPropertySource
        static void configureRedis(DynamicPropertyRegistry registry){
                registry.add("spring.data.redis.host", redis::getHost);
                registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        }

        @Autowired
        private SseEventPublisher publisher;

        @Autowired
        private SseHistoryStore historyStore;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean(name = "sseTransportPublisher")
        private SseEventPublisher transportPublisher;


        @Test
        @DisplayName("이벤트를 발행하면 Redis에 이력이 저장되고 저장된 이벤트가 전송 발행기에 전달된다.")
        void savesHistoryInRedisWhenPublishing(){
                UUID eventId = UUID.randomUUID();
                UUID receiverId = UUID.randomUUID();

                SseEvent<Map<String, String>> event = SseEvent.createNotificationEvent(eventId, receiverId, Map.of("message", "이력 저장 통합 테스트"));
                publisher.publish(event);

                SseHistoryReadResult result = historyStore.readAfter(receiverId, null);
                assertThat(result.gapDetected()).isFalse();
                assertThat(result.entries()).hasSize(1);

                SseHistoryEntry savedEntry = result.entries().get(0);

                JsonNode expectedEventJson = objectMapper.valueToTree(event);
                JsonNode savedEventJson = objectMapper.valueToTree(savedEntry.event());

                assertThat(savedEventJson).isEqualTo(expectedEventJson);
                verify(transportPublisher).publish(savedEntry.event());
                verifyNoMoreInteractions(transportPublisher);
        }

        @Test
        @DisplayName("전송이 실패해도 Redis에 저장된 이벤트 이력은 유지된다.")
        void retainsHistoryInRedisWhenTransportFails(){
                UUID eventId = UUID.randomUUID();
                UUID receiverId = UUID.randomUUID();
                SseEvent<Map<String, String>> event = SseEvent.createNotificationEvent(eventId, receiverId, Map.of("message", "전송 실패시 이력 보존 테스트"));

                SsePublishException failure = new SsePublishException(new DataAccessResourceFailureException("테스트용 전송 실패"));
                doThrow(failure)
                        .when(transportPublisher)
                        .publish(any());

                assertThatThrownBy(() -> publisher.publish(event))
                        .isSameAs(failure);

                SseHistoryReadResult result = historyStore.readAfter(receiverId, null);
                assertThat(result.gapDetected()).isFalse();
                assertThat(result.entries()).hasSize(1);

                SseHistoryEntry savedEntry = result.entries().get(0);

                JsonNode expectedEventJson = objectMapper.valueToTree(event);
                JsonNode savedEventJson = objectMapper.valueToTree(savedEntry.event());

                assertThat(savedEventJson).isEqualTo(expectedEventJson);

                verify(transportPublisher).publish(savedEntry.event());
                verifyNoMoreInteractions(transportPublisher);
        }
}
