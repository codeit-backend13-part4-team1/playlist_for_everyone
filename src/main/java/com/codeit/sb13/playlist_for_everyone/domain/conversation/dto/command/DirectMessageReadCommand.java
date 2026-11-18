package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command;

import java.util.UUID;

/**
 * 기준 메시지까지의 누적 읽음 처리를 요청하는 입력이다.
 *
 * @param conversationId 읽음 처리 대상 대화방 ID
 * @param directMessageId 읽음 범위를 결정할 기준 메시지 ID. 요청자가 보낸 메시지도 기준이 될 수 있다.
 * @param requesterId 컨트롤러가 인증 정보에서 추출한 요청자 ID. 실제 갱신에서는 수신자 조건으로 사용한다.
 */
public record DirectMessageReadCommand(
        UUID conversationId,
        UUID directMessageId,
        UUID requesterId
) {
}
