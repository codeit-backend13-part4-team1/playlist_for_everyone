package com.codeit.sb13.playlist_for_everyone.global.sse.event;

/**
 * 호출부가 특정 메시지 브로커에 의존하지 않도록 분리한 공통 이벤트 발행 계약이다.
 * 현재 Redis Pub/Sub 구현체를 사용하여 서버 간에 이벤트를 전달한다.
 *
 * <p>이벤트를 수신한 각 서버는 SseDeliveryService.sendToLocalConnections를 호출하여
 * 자기 서버에 연결된 수신자의 SSE 연결로 전송한다.
 * 발행 서버도 Redis 구독 경로로 전달받으며, 발행 단계에서 로컬 전송을 직접 수행하지 않는다.
 *
 * <p>로컬 emitter 관리와 브로커 발행은 서로 다른 책임이다.
 * 알림 저장 커밋 후 NotificationSseEventListener가 이 계약을 호출한다.
 * 업무별 알림 생성 요청 변환과 DM 본문 전송 연동, Kafka 구현체는 별도 작업이다.
 */
public interface SseEventPublisher {
    /**
     * 수신자와 논리적 이벤트 ID를 포함한 이벤트를 발행한다.
     * 브라우저의 수신 확인을 반환하는 계약은 아니다. 이력 저장과 재전송 정책은 후속 이슈에서 구체화한다.
     */
    void publish(SseEvent<?> sseEvent);
}
