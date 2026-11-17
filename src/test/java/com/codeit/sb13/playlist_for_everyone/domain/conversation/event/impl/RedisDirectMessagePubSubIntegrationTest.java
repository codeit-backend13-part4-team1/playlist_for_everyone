package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.config.DirectMessageTransportConfig;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessagePublisher;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageDeliveryServiceImpl;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.config.RedisConfig;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

/**
 * 실제 Redis를 통한 발행·구독·역직렬화와 로컬 전달 호출을 검증한다.
 * SimpMessagingTemplate은 Mock이므로 브라우저의 실제 프레임 수신이나 DB 저장 성공을 검증하지 않는다.
 * 추가 컨텍스트를 사용하는 사례도 같은 JVM 안에서 구성되며 개발용 Compose Redis와 무관하다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {
                RedisConfig.class,
                DirectMessageTransportConfig.class,
                DirectMessageDeliveryServiceImpl.class,
                RedisBrokerRegister.class,
                RedisDirectMessagePublisher.class,
                RedisDirectMessageSubscriber.class,
                RedisDirectMessageSubscription.class
        },
        properties = {
                "everyone.dm.websocket.transport=redis",
                "everyone.dm.websocket.subscription-enabled=true",
                "everyone.dm.websocket.redis.channel=test:dm:pubsub"
        }
)
@ImportAutoConfiguration({
        DataRedisAutoConfiguration.class,
        JacksonAutoConfiguration.class
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Redis DM 발행·구독 통합 테스트")
public class RedisDirectMessagePubSubIntegrationTest {

    private static final String CHANNEL = "test:dm:pubsub";

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.2.6-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry){
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", ()-> redis.getMappedPort(6379));
    }

    @Autowired
    private DirectMessagePublisher publisher;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("발행한 DM이 Redis를 거쳐 메시지 정보를 보존하며 해당 대화방으로 한 번 전달된다.")
    void deliversPublishedMessageThroughRedis(){
        MessageDto message = createMessage();
        String destination = getDestination(message.conversationId());

        publisher.publish(message);

        verify(messagingTemplate, Mockito.timeout(5_000)).convertAndSend(destination, message);
        verify(messagingTemplate, after(500).times(1))
                .convertAndSend(destination, message);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("A에서 발행한 DM이 Redis를 거쳐 독립된 A와 B 컨텍스트에 각각 한 번 전달된다.")
    void deliversPublishedMessageToAnotherNode(){
        createAdditionalNodeRunner().run(contextB ->{
            assertThat(contextB).hasNotFailed();
            SimpMessagingTemplate messagingTemplateOnB = contextB.getBean(SimpMessagingTemplate.class);

            assertThat(messagingTemplateOnB).isNotSameAs(messagingTemplate);

            MessageDto message = createMessage();
            String destination = getDestination(message.conversationId());

            publisher.publish(message);
            verify(messagingTemplate, timeout(5_000)).convertAndSend(destination, message);
            verify(messagingTemplateOnB, timeout(5_000)).convertAndSend(destination, message);

            verify(messagingTemplate, after(500).times(1))
                    .convertAndSend(destination, message);
            verify(messagingTemplateOnB, after(500).times(1))
                    .convertAndSend(destination, message);

            verifyNoMoreInteractions(messagingTemplate, messagingTemplateOnB);
        });
    }

    @Test
    @DisplayName("Redis로 수신한 잘못된 JSON은 전달하지 않고 이후 정상 DM은 해당 대화방으로 전달한다.")
    void deliversValidMessageAfterMalformedJson(){
        Long subscriberCount = redisTemplate.convertAndSend(CHANNEL, "{invalid-json}");
        assertThat(subscriberCount).isPositive();

        verify(messagingTemplate, after(500).never())
                .convertAndSend(anyString(), any(Object.class));

        MessageDto message = createMessage();
        String destination = getDestination(message.conversationId());

        publisher.publish(message);
        verify(messagingTemplate, timeout(5_000))
                .convertAndSend(destination, message);
        verify(messagingTemplate, after(500).times(1))
                .convertAndSend(destination, message);

        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Redis 구독 연결이 끊겨도 자동으로 재구독하여 새 DM을 전달한다.")
    void deliversNewMessageAfterSubscriptionReconnects() throws IOException, InterruptedException {
        var disconnected = redis.execInContainer("redis-cli", "--raw", "CLIENT", "KILL", "TYPE", "pubsub");

        assertThat(disconnected.getExitCode()).isZero();
        assertThat(disconnected.getStdout().strip()).isEqualTo("1");

        await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->{
                    var subscriptions = redis.execInContainer("redis-cli", "--raw", "PUBSUB", "NUMSUB", CHANNEL);

                    assertThat(subscriptions.getExitCode()).isZero();
                    assertThat(subscriptions.getStdout().lines().toList())
                            .containsExactly(CHANNEL, "1");
                });

        MessageDto message = createMessage();
        String destination = getDestination(message.conversationId());

        publisher.publish(message);

        verify(messagingTemplate, timeout(5_000))
                .convertAndSend(destination, message);
        verify(messagingTemplate, after(500).times(1))
                .convertAndSend(destination, message);

        verifyNoMoreInteractions(messagingTemplate);
    }

    private String getDestination(UUID conversationId) {
        return "/sub/conversations/" + conversationId + "/direct-messages";
    }

    private MessageDto createMessage() {
        return new MessageDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.of(2026, 11, 16, 10, 20, 30),
                new UserSummary(
                        UUID.randomUUID(),
                        "발신자",
                        "https://example.com/profile.png"
                ),
                new UserSummary(
                        UUID.randomUUID(),
                        "수신자",
                        null
                ),
                "첫 번째 줄\n\"Redis 통합 테스트\" 메시지"
        );
    }

    private ApplicationContextRunner createAdditionalNodeRunner(){
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataRedisAutoConfiguration.class,
                        JacksonAutoConfiguration.class
                ))
                .withUserConfiguration(
                        RedisConfig.class,
                        DirectMessageTransportConfig.class,
                        DirectMessageDeliveryServiceImpl.class,
                        RedisBrokerRegister.class,
                        RedisDirectMessagePublisher.class,
                        RedisDirectMessageSubscriber.class,
                        RedisDirectMessageSubscription.class
                )
                .withPropertyValues(
                        "spring.data.redis.host=" + redis.getHost(),
                        "spring.data.redis.port=" + redis.getMappedPort(6379),
                        "everyone.dm.websocket.transport=redis",
                        "everyone.dm.websocket.subscription-enabled=true",
                        "everyone.dm.websocket.redis.channel=" + CHANNEL
                )
                .withBean(SimpMessagingTemplate.class,
                        () -> mock(SimpMessagingTemplate.class));
    }

}
