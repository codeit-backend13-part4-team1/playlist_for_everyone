package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;

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
