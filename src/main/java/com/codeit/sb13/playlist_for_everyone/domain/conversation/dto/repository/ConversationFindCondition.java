package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import java.util.UUID;

/**
 * 상대방 기준 조회에 필요한 인증된 요청자 ID와 상대방 ID다.
 * 요청 방향을 유지하며, Repository에 전달할 때 ConversationPair로 저장 순서에 맞게 정렬한다.
 */
public record ConversationFindCondition(UUID requesterId, UUID withUserId) {

    public ConversationPair toConversationPair() {
        return new ConversationPair(requesterId, withUserId, requesterId);
    }
}
