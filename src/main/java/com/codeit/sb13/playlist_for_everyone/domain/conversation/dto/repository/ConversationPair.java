package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;

import java.util.UUID;

public record ConversationPair(UUID userAId, UUID userBId, UUID requesterId) {

    public ConversationPair {
        if (userAId.toString().compareTo(userBId.toString()) > 0) {
            UUID temp = userAId;
            userAId = userBId;
            userBId = temp;
        }
    }
}
