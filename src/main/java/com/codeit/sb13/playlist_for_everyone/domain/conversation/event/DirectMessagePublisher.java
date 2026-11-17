package com.codeit.sb13.playlist_for_everyone.domain.conversation.event;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;

/**
 * 저장이 완료된 DM 응답을 서버 간 전송 수단에 발행하는 계약이다.
 * 저장·응답 변환은 호출자의 책임이다. 발행 완료는 개별 WebSocket 클라이언트의 수신 확인이 아니다.
 * 현재 Redis 구현체를 사용하며, 다른 전송 수단을 추가해도 로컬 구독자 전달과 역할을 구분한다.
 */
public interface DirectMessagePublisher {
    void publish(MessageDto message);
}
