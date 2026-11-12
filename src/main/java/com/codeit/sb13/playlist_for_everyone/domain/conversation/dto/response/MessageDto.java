package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 공식 API 명세의 DirectMessageDto JSON 계약을 표현하는 메시지 응답이다.
 * 대화방 응답에서는 최신 메시지가 있을 때 사용하며, sender·receiver는 실제 발신자·수신자 정보다.
 */
public record MessageDto(
        UUID id,
        UUID conversationId,
        LocalDateTime createdAt,
        UserSummary sender,
        UserSummary receiver,
        String content) {
}
