package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConversationCreateRequest(@NotNull UUID withUserId) {

    public ConversationCreateCommand toCommand(UUID requesterId) {
        return new ConversationCreateCommand(requesterId, withUserId);
    }
}
