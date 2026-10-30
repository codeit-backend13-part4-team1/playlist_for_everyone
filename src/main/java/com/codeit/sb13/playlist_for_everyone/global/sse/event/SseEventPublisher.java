package com.codeit.sb13.playlist_for_everyone.global.sse.event;

/**
 * 호출부가 특정 메시지 브로커에 의존하지 않도록 분리한 공통 이벤트 발행 계약이다.
 * 현재 이슈에서는 인터페이스만 정의하고, Redis/Kafka 구현체와 업무 이벤트 연동은 후속 이슈에서 추가한다.
 *
 * <p>향후 발행 구현체가 서버 간에 이벤트를 전달하면, 각 서버의 수신 처리가
 * SseDeliveryService.sendToLocalConnections를 호출하여 자기 서버의 연결에 전송한다.
 * 로컬 emitter 관리와 브로커 발행은 서로 다른 책임이다.
 */
public interface SseEventPublisher {
    /**
     * 수신자와 논리적 이벤트 ID를 포함한 이벤트를 발행한다.
     * 브라우저의 수신 확인을 반환하는 계약은 아니다. 이력 저장과 재전송 정책은 후속 이슈에서 구체화한다.
     */
    void publish(SseEvent<?> sseEvent);
}
