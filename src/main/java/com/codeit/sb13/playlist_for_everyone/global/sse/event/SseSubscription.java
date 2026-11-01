package com.codeit.sb13.playlist_for_everyone.global.sse.event;

/**
 * 브로커의 이벤트 수신을 시작하고 종료하는 계약이다. HTTP SSE 연결 등록은 연결 서비스가 담당한다.
 * 현재 Redis 구현체는 컨텍스트 시작·종료 시 자기 리스너를 공용 컨테이너에 등록·해제한다.
 * 공용 브로커 자원 전체를 종료하는 계약은 아니다.
 */
public interface SseSubscription {
    void start();

    void stop();
}
