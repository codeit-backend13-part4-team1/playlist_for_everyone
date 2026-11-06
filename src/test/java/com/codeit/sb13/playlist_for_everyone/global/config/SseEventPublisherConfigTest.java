package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.impl.HistorySavingSseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.impl.RedisSseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryEntry;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("SSE 공통 발행기 빈 연결")
class SseEventPublisherConfigTest {

    private final SseHistoryStore historyStore = mock(SseHistoryStore.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SseEventPublisherConfig.class,
                    SseTransportConfig.class,
                    RedisSseEventPublisher.class)
            .withPropertyValues(
                    "everyone.sse.transport=redis",
                    "everyone.sse.redis.channel=test:sse:event"
            )
            .withBean(SseHistoryStore.class, () -> historyStore)
            .withBean(StringRedisTemplate.class, () -> redisTemplate)
            .withBean(ObjectMapper.class, () -> objectMapper);

    @Test
    @DisplayName("Redis 설정에서 공통 발행기를 조회하면 이력 저장 발행기가 선택된다.")
    void selectsHistorySavingPublisherAsPrimary(){
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(SseEventPublisher.class))
                    .hasSize(2);

            SseEventPublisher publisher = context.getBean(SseEventPublisher.class);
            assertThat(publisher).isInstanceOf(HistorySavingSseEventPublisher.class);
        });
    }

    @Test
    @DisplayName("공통 발행기를 호출하면 이력을 먼저 저장하고 저장된 이벤트를 Redis에 전달한다.")
    void publishesThroughHistoryAndRedisBeans(){
        contextRunner.run(context ->{
            assertThat(context).hasNotFailed();

            UUID eventId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            Map<String, String> data = Map.of("message", "테스트 알림");
            SseEvent<Map<String, String>> event = SseEvent.createNotificationEvent(eventId, receiverId, data);

            JsonNode jsonData = objectMapper.valueToTree(data);
            SseEvent<JsonNode> historyEvent = SseEvent.createNotificationEvent(eventId, receiverId, jsonData);
            SseHistoryEntry savedEntry = new SseHistoryEntry(new SseHistoryPosition("1000-0"), historyEvent);

            when(historyStore.append(historyEvent)).thenReturn(savedEntry);
            SseEventPublisher publisher = context.getBean(SseEventPublisher.class);
            publisher.publish(event);

            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            InOrder order = inOrder(historyStore, redisTemplate);

            order.verify(historyStore).append(historyEvent);
            order.verify(redisTemplate).convertAndSend(
                    eq("test:sse:event"),
                    messageCaptor.capture()
            );

            JsonNode publishedJson = objectMapper.readTree(messageCaptor.getValue());
            assertThat(publishedJson)
                    .isEqualTo(objectMapper.valueToTree(savedEntry.event()));
        });
    }

}