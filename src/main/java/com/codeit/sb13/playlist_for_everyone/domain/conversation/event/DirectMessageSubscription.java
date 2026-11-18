package com.codeit.sb13.playlist_for_everyone.domain.conversation.event;

/**
 * 서버가 DM 전송 채널을 수신하기 위한 구독의 시작·종료 계약이다.
 * 브라우저의 STOMP SUBSCRIBE와 별개이며, 구체적인 등록 방식과 생명주기 연결은 구현체가 담당한다.
 */
public interface DirectMessageSubscription {
     void start();
     void stop();
}
