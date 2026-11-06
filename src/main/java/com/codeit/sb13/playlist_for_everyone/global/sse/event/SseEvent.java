package com.codeit.sb13.playlist_for_everyone.global.sse.event;

import tools.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.UUID;

/**
 * 알림·DM이 공유하는 내부 전달 형식이다. 특정 도메인의 DTO 타입에 의존하지 않는다.
 *
 * <p>브라우저에는 eventId -> id, eventName -> event, data -> JSON data로 전달한다.
 * receiverId는 서버에서 연결을 찾는 데 사용하며, 이 record 전체를 data로 감싸 보내지 않는다.
 * 호출부는 data에 직렬화 전 객체를 넣고, SseDeliveryService가 연결에 쓰기 전에 JSON으로 변환한다.
 *
 * <p>필드 참조는 변경할 수 없지만 T 객체 내부까지 불변으로 만드는 것은 아니다.
 * 발행 후 payload를 변경하지 않는 방식으로 사용해야 수신 연결 간 내용이 달라지지 않는다.
 *
 * @param eventId 발행 측이 부여한 논리적 이벤트 UUID. null을 허용하지 않으며,
 *                같은 이벤트를 재전달할 때도 유지한다
 * @param receiverId 이벤트를 받을 사용자 UUID. null을 허용하지 않는다
 * @param eventName 프론트엔드가 구독하는 notifications 또는 direct-messages. null을 허용하지 않는다
 * @param data JSON으로 직렬화할 DTO 등의 객체. null을 허용하며, 호출부에서 미리 JSON 문자열로 바꾸지 않는다
 * @param <T> 이벤트 payload의 타입
 */
public record SseEvent<T>(
        UUID eventId,
        UUID receiverId,
        String eventName,
        T data
) {
    public SseEvent {
        Objects.requireNonNull(eventId, "이벤트 ID는 null일 수 없습니다.");
        Objects.requireNonNull(receiverId, "수신자 ID는 null일 수 없습니다.");
        Objects.requireNonNull(eventName, "이벤트 이름은 null일 수 없습니다.");
        if (!Event.exist(eventName)){
            throw new IllegalArgumentException("지원하지 않는 이벤트이름 입니다. eventName=%s".formatted(eventName));
        }
    }

    /**
     * 기존 이벤트 ID와 payload를 유지하면서 이벤트 이름을 notifications로 지정한다.
     * 알림 도메인에서는 저장된 NotificationDto를 넘기지만, 공통 타입은 그 DTO에 의존하지 않는다.
     * 이 팩토리는 새 ID 생성·JSON 변환·발행을 수행하지 않으며, 브라우저 data에는 payload만 전달된다.
     */
    public static <T> SseEvent<T> createNotificationEvent(UUID eventId, UUID receiverId, T data){
        return new SseEvent<>(
                eventId,
                receiverId,
                Event.NOTIFICATIONS.getName(),
                data
        );
    }

    public SseEvent<JsonNode> toHistoryData(JsonNode data){
        return new SseEvent<>(eventId, receiverId, eventName, data);
    }

}
