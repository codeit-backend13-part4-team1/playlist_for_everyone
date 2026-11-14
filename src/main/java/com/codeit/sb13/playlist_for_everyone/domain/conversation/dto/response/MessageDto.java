package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 공식 API 명세의 DirectMessageDto JSON 계약을 표현하는 메시지 응답이다.
 * 대화방 응답의 최신 메시지와 DM 목록의 각 메시지에 공통으로 사용한다.
 * sender·receiver는 요청자의 조회 방향과 관계없이 실제 발신자·수신자 정보이며, 읽음 상태는 포함하지 않는다.
 */
public record MessageDto(
        UUID id,
        UUID conversationId,
        LocalDateTime createdAt,
        UserSummary sender,
        UserSummary receiver,
        String content) {
}
