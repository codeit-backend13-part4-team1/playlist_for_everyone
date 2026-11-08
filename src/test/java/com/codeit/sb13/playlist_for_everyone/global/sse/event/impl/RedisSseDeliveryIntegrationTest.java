package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.config.*;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.impl.RedisSseHistoryStore;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl.InMemorySseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseConnectionCloser;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryCoordinator;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseRecoveryService;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {
                RedisConfig.class,
                SseTransportConfig.class,
                RedisBrokerRegister.class,
                RedisSseEventPublisher.class,
                RedisSseEventSubscriber.class,
                RedisSseSubscription.class,
                SseDeliveryService.class,
                InMemorySseEmitterRepository.class,
                SseEventPublisherConfig.class,
                SseHistoryConfig.class,
                SseRetryConfig.class,
                RedisSseHistoryStore.class,
                SseRecoveryService.class,
                SseDeliveryCoordinator.class,
                SseConnectionCloser.class
        },
        properties = {
                "everyone.sse.transport=redis",
                "everyone.sse.subscription-enabled=true",
                "everyone.sse.redis.channel=test:sse:delivery",
                "everyone.sse.history.retention=10m",
                "everyone.sse.history.max-events-per-user=10",
                "everyone.sse.history.key-prefix=test:sse:delivery:history",
                "everyone.sse.retry.initial-delay=1s",
                "everyone.sse.retry.max-delay=30s",
                "everyone.sse.retry.max-retries=3"
        }
)
@ImportAutoConfiguration({
        DataRedisAutoConfiguration.class,
        JacksonAutoConfiguration.class
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Redis SSE 수신자 연결 전송 통합 테스트")
public class RedisSseDeliveryIntegrationTest {

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

    @Autowired
    private SseEmitterRepository sseEmitterRepository;

    @Autowired
    private SseDeliveryCoordinator deliveryCoordinator;

    @Test
    @DisplayName("Redis를 거친 이벤트가 ID·이름·데이터를 보존하며 발행 서버의 연결에 중복 없이 전달된다.")
    void deliversPublishedEventToReceiverConnection() throws IOException {
        UUID receiverId = UUID.randomUUID();
        Connection connection = Connection.create();
        SseEmitter emitter = mock(SseEmitter.class);

        SseEvent<Map<String, String>> event = new SseEvent<>(
                UUID.randomUUID(),
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "수신자 연결 전송 테스트")
        );

        try {
            deliveryCoordinator.register(receiverId, connection, emitter);
            sseEmitterRepository.save(receiverId, connection, emitter);
            deliveryCoordinator.recover(receiverId, connection, null);
            ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor = ArgumentCaptor.captor();
            publisher.publish(event);
            verify(emitter, timeout(5_000))
                    .send(eventCaptor.capture());
            verify(emitter, after(500).times(1))
                    .send(any(SseEmitter.SseEventBuilder.class));

            String frame = eventCaptor.getValue().build().stream()
                    .map(part -> part.getData().toString())
                    .collect(Collectors.joining());

            assertThat(frame.lines().toList())
                    .contains("id:" + event.eventId(), "event:notifications");

            String dataLine = frame.lines().filter(line -> line.startsWith("data:"))
                    .findFirst()
                    .orElseThrow();

            assertThat(objectMapper.readTree(dataLine.substring("data:".length())))
                    .isEqualTo(objectMapper.valueToTree(event.data()));

        } finally {
            deliveryCoordinator.remove(receiverId, connection);
            sseEmitterRepository.delete(receiverId, connection);
        }
    }

    @Test
    @DisplayName("수신자의 로컬 연결이 여러 개이면 Redis 이벤트를 모든 연결에 전달한다.")
    void deliversPublishedEventToAllReceiverConnections() throws IOException {
        UUID receiverId = UUID.randomUUID();

        Connection firstConnection = Connection.create();
        Connection secondConnection = Connection.create();

        SseEmitter firstEmitter = mock(SseEmitter.class);
        SseEmitter secondEmitter = mock(SseEmitter.class);

        SseEvent<Map<String, String>> event = new SseEvent<>(UUID.randomUUID(),
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "여러 연결 전송 테스트"));

        try {
            deliveryCoordinator.register(receiverId, firstConnection, firstEmitter);
            sseEmitterRepository.save(receiverId, firstConnection, firstEmitter);
            deliveryCoordinator.recover(receiverId, firstConnection, null);

            deliveryCoordinator.register(receiverId, secondConnection, secondEmitter);
            sseEmitterRepository.save(receiverId, secondConnection, secondEmitter);
            deliveryCoordinator.recover(receiverId, secondConnection, null);

            publisher.publish(event);
            verify(firstEmitter, timeout(5_000))
                    .send(any(SseEmitter.SseEventBuilder.class));
            verify(secondEmitter, timeout(5_000))
                    .send(any(SseEmitter.SseEventBuilder.class));

        } finally {
            deliveryCoordinator.remove(receiverId, firstConnection);
            sseEmitterRepository.delete(receiverId, firstConnection);
            deliveryCoordinator.remove(receiverId, secondConnection);
            sseEmitterRepository.delete(receiverId, secondConnection);
        }
    }

    @Test
    @DisplayName("Redis 이벤트는 지정된 수신자에게만 전달하고 다른 사용자에게는 전달하지 않는다.")
    void deliversPublishedEventOnlyReceiver() throws IOException {
        UUID receiverId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Connection receiverConnection = Connection.create();
        Connection otherConnection = Connection.create();

        SseEmitter receiverEmitter = mock(SseEmitter.class);
        SseEmitter otherEmitter = mock(SseEmitter.class);

        SseEvent<Map<String, String>> event = new SseEvent<>(UUID.randomUUID(), receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "수신자 구분 테스트"));

        try {
            deliveryCoordinator.register(receiverId, receiverConnection, receiverEmitter);
            sseEmitterRepository.save(receiverId, receiverConnection, receiverEmitter);
            deliveryCoordinator.recover(receiverId, receiverConnection, null);

            deliveryCoordinator.register(otherUserId, otherConnection, otherEmitter);
            sseEmitterRepository.save(otherUserId, otherConnection, otherEmitter);
            deliveryCoordinator.recover(otherUserId, otherConnection, null);

            publisher.publish(event);

            verify(receiverEmitter, timeout(5_000))
                    .send(any(SseEmitter.SseEventBuilder.class));

            verify(otherEmitter, after(500).never())
                    .send(any(SseEmitter.SseEventBuilder.class));
        } finally {
            deliveryCoordinator.remove(receiverId, receiverConnection);
            sseEmitterRepository.delete(receiverId, receiverConnection);
            deliveryCoordinator.remove(otherUserId, otherConnection);
            sseEmitterRepository.delete(otherUserId, otherConnection);
        }


    }

    @Test
    @DisplayName("A에서 발행한 이벤트가 같은 사용자의 A와 B 연결에 모두 전달된다.")
    void deliversPublishedEventToReceiverConnectionsAcrossNodes(){
        createAdditionalNodeRunner().run(contextB ->{
            assertThat(contextB).hasNotFailed();

            SseEmitterRepository repositoryOnB = contextB.getBean(SseEmitterRepository.class);
            SseDeliveryCoordinator coordinatorOnB = contextB.getBean(SseDeliveryCoordinator.class);

            assertThat(repositoryOnB).isNotSameAs(sseEmitterRepository);
            assertThat(coordinatorOnB).isNotSameAs(deliveryCoordinator);

            UUID receiverId = UUID.randomUUID();
            Connection connectionOnA = Connection.create();
            Connection connectionOnB = Connection.create();

            SseEmitter emitterOnA = mock(SseEmitter.class);
            SseEmitter emitterOnB = mock(SseEmitter.class);

            SseEvent<Map<String, String>> event = new SseEvent<>(UUID.randomUUID(),
                    receiverId,
                    Event.NOTIFICATIONS.getName(),
                    Map.of("message", "서버 간 연결 전송 테스트"));

            try {
                deliveryCoordinator.register(receiverId, connectionOnA, emitterOnA);
                sseEmitterRepository.save(receiverId, connectionOnA, emitterOnA);
                deliveryCoordinator.recover(receiverId, connectionOnA, null);

                coordinatorOnB.register(receiverId, connectionOnB, emitterOnB);
                repositoryOnB.save(receiverId, connectionOnB, emitterOnB);
                coordinatorOnB.recover(receiverId, connectionOnB, null);

                publisher.publish(event);
                verify(emitterOnA, timeout(5_000))
                        .send(any(SseEmitter.SseEventBuilder.class));
                verify(emitterOnB, timeout(5_000))
                        .send(any(SseEmitter.SseEventBuilder.class));
            } finally {
                deliveryCoordinator.remove(receiverId, connectionOnA);
                sseEmitterRepository.delete(receiverId, connectionOnA);
                coordinatorOnB.remove(receiverId, connectionOnB);
                repositoryOnB.delete(receiverId, connectionOnB);
            }
        });
    }

    @Test
    @DisplayName("A가 수신자 연결이 없어도 B의 수신자 연결에 전달한다.")
    void deliversPublishedEventWhenReceiverIsOnlyOnAnotherNode(){
        createAdditionalNodeRunner().run(contextB ->{
            assertThat(contextB).hasNotFailed();
            SseEmitterRepository repositoryOnB = contextB.getBean(SseEmitterRepository.class);
            SseDeliveryCoordinator coordinatorOnB = contextB.getBean(SseDeliveryCoordinator.class);

            assertThat(repositoryOnB).isNotSameAs(sseEmitterRepository);
            assertThat(coordinatorOnB).isNotSameAs(deliveryCoordinator);

            UUID receiverId = UUID.randomUUID();
            Connection connectionOnB = Connection.create();
            SseEmitter emitterOnB = mock(SseEmitter.class);

            SseEvent<Map<String, String>> event = new SseEvent<>(UUID.randomUUID(),
                    receiverId,
                    Event.NOTIFICATIONS.getName(),
                    Map.of("message", "다른 서버에만 연결된 수신자")
            );

            try {
                coordinatorOnB.register(receiverId, connectionOnB, emitterOnB);
                repositoryOnB.save(receiverId, connectionOnB, emitterOnB);
                coordinatorOnB.recover(receiverId, connectionOnB, null);

                assertThat(sseEmitterRepository.findAllByUserId(receiverId)).isEmpty();
                publisher.publish(event);

                verify(emitterOnB, timeout(5_000))
                        .send(any(SseEmitter.SseEventBuilder.class));

            } finally {
                coordinatorOnB.remove(receiverId, connectionOnB);
                repositoryOnB.delete(receiverId, connectionOnB);
            }
        });
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
                        RedisSseSubscription.class,
                        SseDeliveryService.class,
                        InMemorySseEmitterRepository.class,
                        SseEventPublisherConfig.class,
                        SseHistoryConfig.class,
                        SseRetryConfig.class,
                        RedisSseHistoryStore.class,
                        SseRecoveryService.class,
                        SseDeliveryCoordinator.class,
                        SseConnectionCloser.class
                )
                .withPropertyValues(
                        "spring.data.redis.host="+ redis.getHost(),
                        "spring.data.redis.port="+ redis.getMappedPort(6379),
                        "everyone.sse.transport=redis",
                        "everyone.sse.subscription-enabled=true",
                        "everyone.sse.redis.channel=test:sse:delivery",
                        "everyone.sse.history.retention=10m",
                        "everyone.sse.history.max-events-per-user=10",
                        "everyone.sse.history.key-prefix=test:sse:delivery:history",
                        "everyone.sse.retry.initial-delay=1s",
                        "everyone.sse.retry.max-delay=30s",
                        "everyone.sse.retry.max-retries=3"
                );
    }


}
