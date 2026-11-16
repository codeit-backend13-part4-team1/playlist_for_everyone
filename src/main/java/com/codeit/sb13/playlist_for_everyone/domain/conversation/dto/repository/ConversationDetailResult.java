package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;

/**
 * Querydsl 생성자 조회의 대화방·최신 메시지·미읽음 계산 결과를 담는 구현체다.
 *
 * <p>엔티티를 HTTP 응답으로 변환하지 않고 참조 그대로 전달한다. lastMessage는 메시지가 없으면 null이다.
 * record 접근자와 함께 ConversationDetailProjection의 getter 계약을 구현하여 호출 계층이 같은 계약을 사용한다.
 */
public record ConversationDetailResult(
        Conversation conversation,
        DirectMessage lastMessage,
        boolean hasUnread
) implements ConversationDetailProjection{
    @Override
    public Conversation getConversation() {
        return conversation;
    }

    @Override
    public DirectMessage getLastMessage() {
        return lastMessage;
    }

    @Override
    public boolean getHasUnread() {
        return hasUnread;
    }
}
