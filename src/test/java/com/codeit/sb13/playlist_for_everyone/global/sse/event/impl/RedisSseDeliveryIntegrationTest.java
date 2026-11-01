package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.config.RedisConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.SseTransportConfig;
import com.codeit.sb13.playlist_for_everyone.global.service.RedisBrokerRegister;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl.InMemorySseEmitterRepository;
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

import static org.assertj.core.api.Assertions.*;
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
                InMemorySseEmitterRepository.class
        },
        properties = {
                "everyone.sse.transport=redis",
                "everyone.sse.subscription-enabled=true",
                "everyone.sse.redis.channel=test:sse:delivery"
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

        sseEmitterRepository.save(receiverId, connection, emitter);
        try {
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
            sseEmitterRepository.save(receiverId, firstConnection, firstEmitter);
            sseEmitterRepository.save(receiverId, secondConnection, secondEmitter);

            publisher.publish(event);
            verify(firstEmitter, timeout(5_000))
                    .send(any(SseEmitter.SseEventBuilder.class));
            verify(secondEmitter, timeout(5_000))
                    .send(any(SseEmitter.SseEventBuilder.class));

        } finally {
            sseEmitterRepository.delete(receiverId, firstConnection);
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
            sseEmitterRepository.save(receiverId, receiverConnection, receiverEmitter);
            sseEmitterRepository.save(otherUserId, otherConnection, otherEmitter);

            publisher.publish(event);

            verify(receiverEmitter, timeout(5_000))
                    .send(any(SseEmitter.SseEventBuilder.class));

            verify(otherEmitter, after(500).never())
                    .send(any(SseEmitter.SseEventBuilder.class));
        } finally {
            sseEmitterRepository.delete(receiverId, receiverConnection);
            sseEmitterRepository.delete(otherUserId, otherConnection);
        }


    }

    @Test
    @DisplayName("A에서 발행한 이벤트가 같은 사용자의 A와 B 연결에 모두 전달된다.")
    void deliversPublishedEventToReceiverConnectionsAcrossNodes(){
        createAdditionalNodeRunner().run(contextB ->{
            assertThat(contextB).hasNotFailed();

            SseEmitterRepository repositoryOnB = contextB.getBean(SseEmitterRepository.class);

            assertThat(repositoryOnB).isNotSameAs(sseEmitterRepository);

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
                sseEmitterRepository.save(receiverId, connectionOnA, emitterOnA);
                repositoryOnB.save(receiverId, connectionOnB, emitterOnB);

                publisher.publish(event);
                verify(emitterOnA, timeout(5_000))
                        .send(any(SseEmitter.SseEventBuilder.class));
                verify(emitterOnB, timeout(5_000))
                        .send(any(SseEmitter.SseEventBuilder.class));
            } finally {
                sseEmitterRepository.delete(receiverId, connectionOnA);
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
            assertThat(repositoryOnB).isNotSameAs(sseEmitterRepository);

            UUID receiverId = UUID.randomUUID();
            Connection connectionOnB = Connection.create();
            SseEmitter emitterOnB = mock(SseEmitter.class);

            SseEvent<Map<String, String>> event = new SseEvent<>(UUID.randomUUID(),
                    receiverId,
                    Event.NOTIFICATIONS.getName(),
                    Map.of("message", "다른 서버에만 연결된 수신자")
            );

            try {
                repositoryOnB.save(receiverId, connectionOnB, emitterOnB);
                assertThat(sseEmitterRepository.findAllByUserId(receiverId)).isEmpty();
                publisher.publish(event);

                verify(emitterOnB, timeout(5_000))
                        .send(any(SseEmitter.SseEventBuilder.class));

            } finally {
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
                        InMemorySseEmitterRepository.class
                )
                .withPropertyValues(
                        "spring.data.redis.host="+ redis.getHost(),
                        "spring.data.redis.port="+ redis.getMappedPort(6379),
                        "everyone.sse.transport=redis",
                        "everyone.sse.subscription-enabled=true",
                        "everyone.sse.redis.channel=test:sse:delivery"
                );
    }


}
