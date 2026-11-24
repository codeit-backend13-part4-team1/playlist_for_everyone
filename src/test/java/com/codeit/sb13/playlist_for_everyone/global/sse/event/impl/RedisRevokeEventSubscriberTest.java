package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.LoginSession;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis SSE 세션 폐기 수신")
class RedisRevokeEventSubscriberTest {

    @Mock
    private SseDeliveryService deliveryService;

    private RedisRevokeEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new RedisRevokeEventSubscriber(JsonMapper.builder().build(), deliveryService);
    }

    @Test
    @DisplayName("정상 폐기 메시지의 사용자 ID와 세션 ID를 로컬 SSE 서비스에 전달한다.")
    void delegatesValidRevocation() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String payload = """
                {"userId":"%s","loginSessionId":"%s"}
                """.formatted(userId, sessionId);

        subscriber.onMessage(message(payload), null);

        verify(deliveryService).revoke(userId, new LoginSession(sessionId));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidMessages")
    @DisplayName("잘못된 폐기 메시지를 건너뛰고 연결 종료를 요청하지 않는다.")
    void ignoresInvalidRevocation(String scenario, String payload) {
        DefaultMessage message = message(payload);

        assertThatCode(() -> subscriber.onMessage(message, null)).doesNotThrowAnyException();
        verifyNoInteractions(deliveryService);
    }

    private static DefaultMessage message(String payload) {
        return new DefaultMessage("test:sse:revocations".getBytes(UTF_8), payload.getBytes(UTF_8));
    }

    private static Stream<Arguments> invalidMessages() {
        return Stream.of(
                Arguments.of("잘못된 JSON", "{invalid-json}"),
                Arguments.of("JSON null", "null"),
                Arguments.of("사용자 ID 누락", "{\"loginSessionId\":\"%s\"}".formatted(UUID.randomUUID())),
                Arguments.of("로그인 세션 ID 누락", "{\"userId\":\"%s\"}".formatted(UUID.randomUUID()))
        );
    }
}
