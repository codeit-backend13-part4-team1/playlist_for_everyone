package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;

public interface ConversationDetailProjection {
    Conversation getConversation();

    DirectMessage getLastMessage();

    boolean getHasUnread();
}
