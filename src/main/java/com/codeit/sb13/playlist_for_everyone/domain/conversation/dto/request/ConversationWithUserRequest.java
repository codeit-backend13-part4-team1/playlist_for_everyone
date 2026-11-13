package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindCondition;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConversationWithUserRequest(
        @NotNull
        UUID userId) {

        public ConversationFindCondition toConversationFindCondition(UUID requesterId){
                return new ConversationFindCondition(requesterId, userId);
        }
}
