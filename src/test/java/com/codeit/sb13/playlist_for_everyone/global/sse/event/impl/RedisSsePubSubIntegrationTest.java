package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.config.RedisConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.SseTransportConfig;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {
                RedisConfig.class,
                SseTransportConfig.class,
                RedisBrokerRegister.class,
                RedisSseEventPublisher.class,
                RedisSseEventSubscriber.class,
                RedisSseSubscription.class
        },
        properties = {
                "everyone.sse.transport=redis",
                "everyone.sse.subscription-enabled=true",
                "everyone.sse.redis.channel=test:sse:pubsub"
        }
)
@ImportAutoConfiguration({
        DataRedisAutoConfiguration.class,
        JacksonAutoConfiguration.class
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Redis SSE 발행·구독 통합 테스트")
public class RedisSsePubSubIntegrationTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry){
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", ()-> redis.getMappedPort(6379));
    }

    @Autowired
    private SseEventPublisher publisher;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SseDeliveryService sseDeliveryService;

    @Test
    @DisplayName("발행한 이벤트가 Redis를 거쳐 식별자와 이름 및 중첩 JSON 데이터를 보존해 전달된다.")
    void deliversPublishedEventThroughRedis(){
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        Map<String, Object> data = Map.of("message", "Redis 통합 테스트 알림",
                "sender", Map.of(
                        "id", UUID.randomUUID().toString(),
                        "name","테스트 사용자"
                ),
                "tags", List.of("영화", "드라마")
        );

        SseEvent<Map<String, Object>> event = new SseEvent<>(eventId, receiverId, Event.NOTIFICATIONS.getName(), data);

        ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.captor();
        publisher.publish(event);

        verify(sseDeliveryService, timeout(5_000)).sendToLocalConnections(eventCaptor.capture());

        SseEvent<?> received = eventCaptor.getValue();

        assertThat(received.eventId()).isEqualTo(eventId);
        assertThat(received.receiverId()).isEqualTo(receiverId);
        assertThat(received.eventName()).isEqualTo(Event.NOTIFICATIONS.getName());

        JsonNode receivedData = objectMapper.valueToTree(received.data());
        JsonNode expectedData = objectMapper.valueToTree(data);

        assertThat(receivedData).isEqualTo(expectedData);
    }

    @Test
    @DisplayName("A에서 발행한 이벤트가 Redis를 거쳐 독립된 B 컨텍스트에 전달된다.")
    void deliversPublishedEventToAnotherNode(){
        createAdditionalNodeRunner().run(contextB ->{
            assertThat(contextB).hasNotFailed();
            SseDeliveryService deliveryOnB = contextB.getBean(SseDeliveryService.class);

            assertThat(deliveryOnB).isNotSameAs(sseDeliveryService);

            UUID eventId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            Map<String, String> data = Map.of("message", "다른 서버로 전달할 알림");
            SseEvent<Map<String, String>> event = new SseEvent<>(
                    eventId,
                    receiverId,
                    Event.NOTIFICATIONS.getName(),
                    data
            );

            ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.captor();

            publisher.publish(event);

            verify(deliveryOnB, timeout(5_000))
                    .sendToLocalConnections(eventCaptor.capture());
            verify(sseDeliveryService, timeout(5_000))
                    .sendToLocalConnections(argThat(receivedEvent ->
                            eventId.equals(receivedEvent.eventId())));

            SseEvent<?> received = eventCaptor.getValue();

            assertThat(received.eventId()).isEqualTo(eventId);
            assertThat(received.receiverId()).isEqualTo(receiverId);
            assertThat(received.eventName()).isEqualTo(Event.NOTIFICATIONS.getName());

            JsonNode receivedData = objectMapper.valueToTree(received.data());
            JsonNode expectedData = objectMapper.valueToTree(data);

            assertThat(receivedData).isEqualTo(expectedData);
        });
    }

    @Test
    @DisplayName("Redis 구독 연결이 끊어져도 자동으로 재구독하여 새 이벤트를 전달한다.")
    void deliversNewEventAfterSubscriptionReconnects() throws IOException, InterruptedException {
        var disconnected = redis.execInContainer("redis-cli", "--raw", "CLIENT", "KILL", "TYPE", "pubsub");

        assertThat(disconnected.getExitCode()).isZero();
        assertThat(disconnected.getStdout().strip()).isEqualTo("1");

        await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(()->{
                    var subscriptions = redis.execInContainer("redis-cli", "--raw", "PUBSUB", "NUMSUB", "test:sse:pubsub");
                    assertThat(subscriptions.getExitCode()).isZero();
                    assertThat(subscriptions.getStdout().lines().toList())
                            .containsExactly("test:sse:pubsub", "1");
                });

        SseEvent<Map<String, String>> event = new SseEvent<>(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "재구독 이후 발행한 알림")
        );

        publisher.publish(event);
        verify(sseDeliveryService, timeout(5_000))
                .sendToLocalConnections(argThat(received ->
                        event.eventId().equals(received.eventId())));

    }

    private ApplicationContextRunner createAdditionalNodeRunner(){
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataRedisAutoConfiguration.class,
                        JacksonAutoConfiguration.class
                ))
                .withUserConfiguration(
                        RedisConfig.class,
                        SseTransportConfig.class,
                        RedisBrokerRegister.class,
                        RedisSseEventPublisher.class,
                        RedisSseEventSubscriber.class,
                        RedisSseSubscription.class
                )
                .withPropertyValues(
                        "spring.data.redis.host=" + redis.getHost(),
                        "spring.data.redis.port=" + redis.getMappedPort(6379),
                        "everyone.sse.transport=redis",
                        "everyone.sse.subscription-enabled=true",
                        "everyone.sse.redis.channel=test:sse:pubsub"
                ).withBean(SseDeliveryService.class,
                        ()-> mock(SseDeliveryService.class));
    }


}
