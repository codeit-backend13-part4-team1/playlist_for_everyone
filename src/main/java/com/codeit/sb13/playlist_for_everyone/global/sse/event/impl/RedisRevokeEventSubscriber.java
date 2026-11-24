package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.LoginSession;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.RevokeEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

/**
 * Redis 폐기 메시지에서 사용자 ID와 로그인 세션 ID를 읽고 로컬 SSE 서비스에 전달한다.
 * 변환 실패는 해당 메시지만 건너뛰고, 서비스 호출은 변환 예외 처리 밖에서 수행한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "everyone.sse",
        name = "transport",
        havingValue = "redis"
)
@Slf4j
public class RedisRevokeEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SseDeliveryService sseDeliveryService;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        readRevocation(message).ifPresent(event ->
                sseDeliveryService.revoke(event.userId(), new LoginSession(event.loginSessionId())));
    }

    private Optional<RevokeEvent<?>> readRevocation(Message message) {
        try {
            RevokeEvent<?> event = objectMapper.readValue(message.getBody(), new TypeReference<>() {});
            return Optional.ofNullable(event);
        } catch (JacksonException e) {
            logMessageConversionFailure(message, e);
            return Optional.empty();
        }
    }

    /**
     * 실패한 경우에만 JSON을 다시 읽어 로그용 식별자를 추출한다.
     * 검증한 UUID와 최초 변환 실패의 예외 타입만 기록한다.
     */
    private void logMessageConversionFailure(Message message, JacksonException cause) {
        JsonNode root = null;
        try {
            root = objectMapper.readTree(message.getBody());
        } catch (JacksonException ignored) {
            // JSON 자체를 읽을 수 없으면 식별자를 null로 기록한다.
        }

        log.warn("SSE 세션 폐기 메시지 변환에 실패했습니다. userId={}, loginSessionId={}, exceptionType={}",
                extractUuid(root, "userId"),
                extractUuid(root, "loginSessionId"),
                cause.getClass().getSimpleName()
        );
    }

    // 식별자가 누락되거나 UUID로 변환되지 않으면 원본 문자열 대신 null을 기록한다.
    private @Nullable UUID extractUuid(@Nullable JsonNode root, String fieldName) {
        if (root == null) {
            return null;
        }
        JsonNode value = root.path(fieldName);
        if (!value.isString()) {
            return null;
        }

        try {
            return UUID.fromString(value.asString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
