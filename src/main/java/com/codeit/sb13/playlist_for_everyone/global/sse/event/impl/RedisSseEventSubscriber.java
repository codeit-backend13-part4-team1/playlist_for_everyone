package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
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
 * Redis 이벤트를 {@code SseEvent<JsonNode>}로 읽어 현재 서버의 후속 이력 조회를 요청한다.
 * 실제 HTTP 데이터는 공유 이력에서 읽으며, 메시지를 받았다는 사실만으로 수신 완료를 판단하지 않는다.
 * 알림·DM DTO 타입에 의존하지 않으며, 수신한 메시지를 다시 Redis로 발행하지 않는다.
 * 수신자 연결이 없는 경우의 정상 종료와 개별 연결 전송 실패 처리는 SseDeliveryService가 담당한다.
 *
 * <p>이벤트 변환에 실패하면 해당 메시지만 건너뛴다. 로컬 전송 호출은 변환 예외 처리 밖에 두어
 * 전송 중 발생한 예외를 역직렬화 실패로 잘못 분류하지 않는다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "everyone.sse",
        name = "transport",
        havingValue = "redis"
)
@Slf4j
public class RedisSseEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SseDeliveryService sseDeliveryService;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
       convertMessageToSseEvent(message)
               .ifPresent(sseDeliveryService::sendToLocalConnections);
    }

    private Optional<SseEvent<JsonNode>> convertMessageToSseEvent(Message message) {
        try {
            return objectMapper.readValue(message.getBody(),
                    new TypeReference<>() {});
        }catch (JacksonException e){
            logMessageConversionFailure(message, e);
            return Optional.empty();
        }
    }

    /**
     * 실패한 경우에만 JSON을 다시 읽어 로그용 식별자를 추출한다.
     * 원문·data·예외 메시지는 기록하지 않고, 최초 이벤트 변환 실패의 예외 타입을 남긴다.
     */
    private void logMessageConversionFailure(Message message, JacksonException cause){
        JsonNode root = null;
        try {
            root = objectMapper.readTree(message.getBody());
        } catch (JacksonException ignored){
            // JSON 자체를 읽을 수 없으면 식별자를 null로 기록한다.
        }

        log.warn("SSE Redis 메시지 변환에 실패하여 전송을 건너뜁니다. eventId={}, receiverId={}, exceptionType={}",
                extractUuid(root,"eventId"),
                extractUuid(root, "receiverId"),
                cause.getClass().getSimpleName()
        );
    }

    // 식별자가 누락되거나 UUID로 변환되지 않으면 원본 문자열 대신 null을 기록한다.
    private @Nullable UUID extractUuid(@Nullable JsonNode root, String fieldName){
        if (root == null){
            return null;

        }
        JsonNode value = root.path(fieldName);
        if(!value.isString()){
            return null;
        }

        try {
            return UUID.fromString(value.asString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
