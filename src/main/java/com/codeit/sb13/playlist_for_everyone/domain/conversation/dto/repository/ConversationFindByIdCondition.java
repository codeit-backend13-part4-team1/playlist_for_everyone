package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import java.util.UUID;

/**
 * 인증된 요청자 ID와 조회할 대화방 ID를 함께 전달한다.
 * Repository는 두 값을 사용해 대화방 식별 조건과 참여자 조건을 동시에 적용한다.
 */
public record ConversationFindByIdCondition(
        UUID requesterId,
        UUID conversationId
) {
}
